package com.oriondev.moneywallet.api.saf

import android.content.Context
import android.content.UriPermission
import android.net.Uri
import android.webkit.MimeTypeMap

import com.oriondev.moneywallet.api.AbstractBackendServiceAPI
import com.oriondev.moneywallet.api.BackendException
import com.oriondev.moneywallet.model.IFile
import com.oriondev.moneywallet.model.SAFFile
import com.oriondev.moneywallet.utils.ProgressInputStream
import com.oriondev.moneywallet.utils.ProgressOutputStream

import org.apache.commons.io.IOUtils

import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.LinkedList
import java.util.Locale
import java.util.Objects

import androidx.documentfile.provider.DocumentFile

import com.oriondev.moneywallet.api.saf.SAFBackendService.Companion.getUri

class SAFBackendServiceAPI : AbstractBackendServiceAPI<SAFFile> {

    private val mContext: Context
    private val mRoot: DocumentFile?

    @Throws(BackendException::class)
    constructor(context: Context) : super(SAFFile::class.java) {
        mContext = context
        val uri = getUri(context)
        if (uri == null) {
            throw BackendException("SAFBackendService not enabled")
        }
        val permission = getPermissionFor(getUri(context))
        if (permission == null || !(permission.isReadPermission && permission.isWritePermission)) {
            throw BackendException("Root uri not accessible (no permission)")
        }
        mRoot = DocumentFile.fromTreeUri(mContext, permission.uri)
    }

    private fun getPermissionFor(uri: Uri?): UriPermission? {
        for (permission in mContext.contentResolver.persistedUriPermissions) {
            if (Objects.equals(permission.uri, uri)) {
                return permission
            }
        }
        return null
    }

    @Throws(BackendException::class)
    public override fun upload(folder: SAFFile?, file: File, listener: ProgressInputStream.UploadProgressListener?): SAFFile {
        val docFile = createDocumentFile(folder, file)
        if (docFile == null) {
            throw BackendException(
                    String.format("Couldn't create output file %s under %s",
                            file.name,
                            if (folder == null) "root directory" else folder.getName()
                    )
            )
        }

        try {
            openUploadInputStream(file, listener).use { `in` ->
                openUploadOutputStream(docFile).use { out ->
                    IOUtils.copy(`in`, out)
                }
            }
        } catch (e: IOException) {
            throw BackendException(
                    String.format("Failed to upload %s to %s", file.name, docFile.uri),
                    e, true
            )
        }
        return SAFFile(docFile)
    }

    private fun openUploadOutputStream(docFile: DocumentFile): OutputStream? {
        val out: OutputStream?
        try {
            out = mContext.contentResolver.openOutputStream(docFile.uri)
        } catch (e: FileNotFoundException) {
            throw AssertionError("Document file should have been created!")
        }
        return out
    }

    @Throws(BackendException::class)
    private fun openUploadInputStream(file: File, listener: ProgressInputStream.UploadProgressListener?): InputStream {
        val `in`: InputStream
        try {
            `in` = ProgressInputStream(file, listener!!)
        } catch (e: FileNotFoundException) {
            throw BackendException(
                    String.format("File '%s' doesn't exist", file.name), e
            )
        } catch (e: IOException) {
            throw BackendException(
                    String.format("Couldn't access file '%s'", file.name), e
            )
        }
        return `in`
    }

    @Throws(BackendException::class)
    private fun createDocumentFile(folder: SAFFile?, file: File): DocumentFile? {
        val docFolder: DocumentFile?
        if (folder == null) {
            docFolder = mRoot
        } else {
            docFolder = DocumentFile.fromTreeUri(mContext, folder.getUri())
            if (docFolder == null) {
                throw BackendException(
                        String.format("Couldn't open folder %s to create file %s",
                                folder.getUri(), file.name)
                )
            }
        }
        return docFolder!!.createFile(getFileType(file), file.name)
    }

    private fun getFileType(file: File): String {
        val name = file.name
        val lastDot = name.lastIndexOf('.')
        if (lastDot >= 0) {
            val extension = name.substring(lastDot + 1).lowercase(Locale.getDefault())
            val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
            if (mime != null) {
                return mime
            }
        }

        return "application/octet-stream"
    }

    @Throws(BackendException::class)
    public override fun download(folder: File?, file: SAFFile, listener: ProgressOutputStream.DownloadProgressListener?): File {
        val destination = File(folder, file.getName())

        try {
            openDownloadInputStream(file).use { `in` ->
                openDownloadOutputStream(file, destination, listener).use { out ->
                    IOUtils.copy(`in`, out)
                }
            }
        } catch (e: IOException) {
            throw BackendException(
                    String.format("Failed to download %s to %s",
                            file.getName(),
                            destination.name
                    ), e, true
            )
        }
        return destination
    }

    @Throws(BackendException::class)
    private fun openDownloadInputStream(file: SAFFile): InputStream {
        val `in`: InputStream?
        try {
            `in` = mContext.contentResolver.openInputStream(file.getUri())
        } catch (e: FileNotFoundException) {
            throw BackendException(
                    String.format("Unable to open uri %s", file.getUri()), e, true
            )
        }

        if (`in` == null) {
            throw BackendException(
                    String.format("Couldn't open file %s its provider has recently crashed",
                            file.getUri()
                    ), true
            )
        }
        return `in`
    }

    @Throws(BackendException::class)
    private fun openDownloadOutputStream(source: SAFFile, destination: File, listener: ProgressOutputStream.DownloadProgressListener?): OutputStream {
        val out: OutputStream
        try {
            out = ProgressOutputStream(destination, source.getSize(), listener!!)
        } catch (e: FileNotFoundException) {
            throw BackendException(
                    String.format(
                            "Couldn't open '%s' for writing (might be a folder or inaccessible)",
                            destination.name
                    ), e, true
            )
        }
        return out
    }

    @Throws(BackendException::class)
    public override fun list(folder: SAFFile?): List<IFile> {
        val docFolder: DocumentFile?
        if (folder == null) {
            docFolder = mRoot
        } else {
            docFolder = DocumentFile.fromTreeUri(mContext, folder.getUri())
            if (docFolder == null) {
                throw BackendException(
                    String.format("Couldn't access folder '%s' " +
                            "(maybe this was called on SDK_Version <21)", folder.getUri())
                )
            }
        }
        val folderContents = LinkedList<IFile>()
        for (file in docFolder!!.listFiles()) {
            folderContents.add(SAFFile(file))
        }
        return folderContents
    }

    @Throws(BackendException::class)
    public override fun newFolder(parent: SAFFile?, name: String): SAFFile {
        // No parent means the top of the granted tree, which is where the explorer starts and
        // is the only place a first folder can be made. Reading a uri off it threw instead, and
        // BackendHandlerIntentService catches BackendException only, so it took the app down.
        // upload and list already read a missing parent this way.
        val docParent = if (parent == null) mRoot
                else DocumentFile.fromTreeUri(mContext, parent.getUri())
        val parentName = if (parent == null) "root directory" else parent.getUri().toString()
        if (docParent == null) {
            throw BackendException(
                    String.format("Couldn't access parent '%s'", parentName)
            )
        }
        val docFolder = docParent.createDirectory(name)
        if (docFolder == null) {
            throw BackendException(
                    String.format(
                            "Couldn't create folder '%s' under parent '%s'", name, parentName
                    )
            )
        }
        return SAFFile(docFolder)
    }
}
