package one.mixin.android.util.backup

import com.drive.demo.backup.Result
import com.drive.demo.backup.Result.FAILURE
import com.drive.demo.backup.Result.NOT_FOUND
import com.drive.demo.backup.Result.SUCCESS
import com.google.android.gms.drive.DriveFile
import com.google.android.gms.drive.DriveFolder
import com.google.android.gms.drive.DriveId
import com.google.android.gms.drive.DriveResourceClient
import com.google.android.gms.drive.Metadata
import com.google.android.gms.drive.query.Filters
import com.google.android.gms.drive.query.Query
import com.google.android.gms.drive.query.SearchableField
import com.google.android.gms.tasks.Tasks
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import one.mixin.android.db.MixinDatabase
import one.mixin.android.util.ZipUtil
import java.io.File
import java.util.concurrent.ExecutionException
import kotlin.coroutines.CoroutineContext

class DataBaseBackupManager private constructor(driveResourceClient: DriveResourceClient,
    private val dbName: String,
    private val folderName: String,
    private val getDbFile: () -> File?,
    private val minVersion: Int? = null,
    private val currentVersion: Int? = null) : BackupManager(driveResourceClient) {

    companion object {
        private val lock = Any()
        private var INSTANCE: DataBaseBackupManager? = null

        fun getManager(driveResourceClient: DriveResourceClient, dbName: String,
            folderName: String, getDbFile: () -> File?, minVersion: Int? = null,
            currentVersion: Int? = null): DataBaseBackupManager {
            synchronized(lock) {
                if (INSTANCE == null || INSTANCE?.getFolderName() != folderName) {
                    INSTANCE = DataBaseBackupManager(driveResourceClient, dbName, folderName, getDbFile,
                        minVersion,
                        currentVersion)
                }
                return INSTANCE as DataBaseBackupManager
            }
        }
    }

    fun getFolderName() = folderName

    private fun createDirectory() {
        val metaData = isRootFolderExists(folderName)
        if (metaData == null) {
            createRootFolder(folderName)
        }
    }

    fun backup(callback: (Result) -> Unit) {
        GlobalScope.launch {
            createDirectory()
            val dbFile = getDbFile() ?: return@launch callback(NOT_FOUND)
            val metadata = isRootFolderExists(folderName)
            if (metadata != null) {
                MixinDatabase.checkPoint()
                uploadDatabase(this.coroutineContext, metadata.driveId, dbFile, currentVersion, callback)
            } else {
                withContext(Dispatchers.Main) {
                    callback(FAILURE)
                }
            }
        }
    }

    fun findBackup(callback: (Result, Metadata?) -> Unit) {
        GlobalScope.launch {
            val parentMetadata = isRootFolderExists(folderName)
            if (parentMetadata == null) {
                withContext(Dispatchers.Main) {
                    callback(NOT_FOUND, null)
                }
            }
            val metaData = findDbFiles(parentMetadata!!.driveId.asDriveFolder(), dbName) { title ->
                if (currentVersion != null && minVersion != null) {
                    val version = title.split("_")[1].toInt()
                    version <= currentVersion && version >= minVersion
                } else {
                    title.contentEquals(dbName)
                }
            }?.run {
                if (!this.isEmpty()) {
                    this[0]
                } else {
                    null
                }
            }
            if (metaData != null) {
                withContext(Dispatchers.Main) {
                    callback(SUCCESS, metaData)
                }
            } else {
                withContext(Dispatchers.Main) {
                    callback(NOT_FOUND, null)
                }
            }
        }
    }

    fun restoreDatabase(callback: (Result) -> Unit) {
        GlobalScope.launch {
            val parentMetadata = isRootFolderExists(folderName)
            if (parentMetadata == null) {
                withContext(Dispatchers.Main) {
                    callback(NOT_FOUND)
                }
            }
            val metaData = findDbFiles(parentMetadata!!.driveId.asDriveFolder(), dbName) { title ->
                if (currentVersion != null && minVersion != null) {
                    val version = title.split("_")[1].toInt()
                    version <= currentVersion && version >= minVersion
                } else {
                    title.contentEquals(dbName)
                }
            }?.run {
                if (!this.isEmpty()) {
                    this[0]
                } else {
                    null
                }
            }
            if (metaData != null) {
                val file = getDbFile() ?: return@launch withContext(Dispatchers.Main) { callback(NOT_FOUND) }
                val zip = File("${file.parent}${File.separator}${file.name}.zip")
                restore(metaData.driveId.asDriveFile(), zip.absolutePath, callback)
                if (zip.exists()) {
                    file.delete()
                    File("${file.absolutePath}-wal").delete()
                    File("${file.absolutePath}-shm").delete()
                    ZipUtil.unZipFolder(zip.absolutePath, zip.parent)
                    zip.delete()
                } else {
                    callback(FAILURE)
                }
            } else {
                withContext(Dispatchers.Main) {
                    callback(NOT_FOUND)
                }
            }
        }
    }

    private fun findDbFiles(driveFile: DriveFolder, dbName: String,
        attachCheck: ((String) -> Boolean)? = null): List<Metadata>? {
        val result = mutableListOf<Metadata>()
        val query = Query.Builder()
            .addFilter(Filters.contains(SearchableField.TITLE, dbName))
            .build()
        val queryTask = resourceClient.queryChildren(driveFile, query)
        try {
            val buffer = Tasks.await(queryTask)
            for (b in buffer) {
                val title = b.title
                if (attachCheck != null && attachCheck(title)) {
                    result.add(b)
                } else if (title == dbName) {
                    result.add(b)
                }
            }
        } catch (e: ExecutionException) {
            e.printStackTrace()
        } catch (e: InterruptedException) {
            e.printStackTrace()
        }
        return result
    }

    private fun clearOtherDb(title: String) {
        val query = Query.Builder()
            .addFilter(Filters.contains(SearchableField.TITLE, "db"))
            .build()
        val queryTask = resourceClient.query(query)
        val metadataBuffer = Tasks.await(queryTask)
        for (b in metadataBuffer) {
            if (b.title != title) {
                val deleteTask = deleteDriveFile(b.driveId.asDriveFile())
                Tasks.await(deleteTask)
            }
        }
    }

    private suspend fun uploadDatabase(context: CoroutineContext, driveId: DriveId, file: File,
        version: Int? = null,
        callback: (Result) -> Unit): DriveFile? {
        val d = GlobalScope.async(context) {
            val title = if (version != null) {
                "${file.name}_$version"
            } else {
                file.name
            }
            val zip = File("${file.parent}${File.separator}${file.name}.zip")
            ZipUtil.zipFolder(file.absolutePath, zip.absolutePath)
            val uploadTask = uploadToAppFolder(driveId, zip, title)
            val driveFile = Tasks.await(uploadTask!!)
            if (driveFile != null && uploadTask.isSuccessful) {
                withContext(Dispatchers.Main) {
                    callback(SUCCESS)
                }
                clearOtherDb(title)
            } else {
                withContext(Dispatchers.Main) {
                    callback(FAILURE)
                }
            }
            zip.delete()
            return@async driveFile
        }
        return d.await()
    }
}