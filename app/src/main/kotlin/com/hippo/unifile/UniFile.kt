/*
 * Copyright (C) 2015 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.hippo.unifile

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.annotation.RequiresApi
import java.io.File

/**
 * In Android files can be accessed via [java.io.File] and [android.net.Uri].
 * The UniFile is designed to emulate File interface for both File and Uri.
 */
abstract class UniFile internal constructor(private val parent: UniFile?) {
    /**
     * Create a new file as a direct child of this directory.
     *
     * @param displayName name of new file
     * @return file representing newly created document, or null if failed
     * @see android.provider.DocumentsContract.createDocument
     */
    abstract fun createFile(displayName: String): UniFile?

    /**
     * Create a new directory as a direct child of this directory.
     *
     * @param displayName name of new directory
     * @return file representing newly created directory, or null if failed
     * @see android.provider.DocumentsContract.createDocument
     */
    abstract fun createDirectory(displayName: String): UniFile?

    /**
     * Return a Uri for the underlying document represented by this file. This
     * can be used with other platform APIs to manipulate or share the
     * underlying content. You can use [.isTreeUri] to
     * test if the returned Uri is backed by a
     * [android.provider.DocumentsProvider].
     *
     * @return uri of the file
     * @see Intent.setData
     * @see Intent.setClipData
     * @see ContentResolver.openInputStream
     * @see ContentResolver.openOutputStream
     * @see ContentResolver.openFileDescriptor
     */
    abstract val uri: Uri

    /**
     * Return the display name of this file.
     *
     * @return name of the file, or null if failed
     * @see android.provider.DocumentsContract.Document.COLUMN_DISPLAY_NAME
     */
    abstract val name: String?

    /**
     * Return the MIME type of this file.
     *
     * @return MIME type of the file, or null if failed
     * @see android.provider.DocumentsContract.Document.COLUMN_MIME_TYPE
     */
    abstract val type: String?

    /**
     * Return the parent file of this file. Only defined inside of the
     * user-selected tree; you can never escape above the top of the tree.
     *
     *
     * The underlying [android.provider.DocumentsProvider] only defines a
     * forward mapping from parent to child, so the reverse mapping of child to
     * parent offered here is purely a convenience method, and it may be
     * incorrect if the underlying tree structure changes.
     *
     * @return parent of the file, or null if it is the top of the file tree
     */
    val parentFile: UniFile?
        get() = parent

    /**
     * Indicates if this file represents a *directory*.
     *
     * @return `true` if this file is a directory, `false`
     * otherwise.
     * @see android.provider.DocumentsContract.Document.MIME_TYPE_DIR
     */
    abstract val isDirectory: Boolean

    /**
     * Indicates if this file represents a *file*.
     *
     * @return `true` if this file is a file, `false` otherwise.
     * @see android.provider.DocumentsContract.Document.COLUMN_MIME_TYPE
     */
    abstract val isFile: Boolean

    /**
     * Returns the time when this file was last modified, measured in
     * milliseconds since January 1st, 1970, midnight. Returns -1 if the file
     * does not exist, or if the modified time is unknown.
     *
     * @return the time when this file was last modified, `-1L` if can't get it
     * @see android.provider.DocumentsContract.Document.COLUMN_LAST_MODIFIED
     */
    abstract fun lastModified(): Long

    /**
     * Returns the length of this file in bytes. Returns -1 if the file does not
     * exist, or if the length is unknown. The result for a directory is not
     * defined.
     *
     * @return the number of bytes in this file, `-1L` if can't get it
     * @see android.provider.DocumentsContract.Document.COLUMN_SIZE
     */
    abstract fun length(): Long

    /**
     * Indicates whether the current context is allowed to read from this file.
     *
     * @return `true` if this file can be read, `false` otherwise.
     */
    abstract fun canRead(): Boolean

    /**
     * Indicates whether the current context is allowed to write to this file.
     *
     * @return `true` if this file can be written, `false`
     * otherwise.
     * @see android.provider.DocumentsContract.Document.COLUMN_FLAGS
     *
     * @see android.provider.DocumentsContract.Document.FLAG_SUPPORTS_DELETE
     *
     * @see android.provider.DocumentsContract.Document.FLAG_SUPPORTS_WRITE
     *
     * @see android.provider.DocumentsContract.Document.FLAG_DIR_SUPPORTS_CREATE
     */
    abstract fun canWrite(): Boolean

    /**
     * It works like mkdirs, but it will return true if the UniFile is directory
     *
     * @return `true` if the directory was created
     * or if the directory already existed.
     */
    abstract fun ensureDir(): Boolean

    /**
     * Make sure the UniFile is file
     *
     * @return `true` if the file can be created
     * or if the file already existed.
     */
    abstract fun ensureFile(): Boolean

    /**
     * Get child file of this directory, the child might not exist.
     *
     * @return the child file
     */
    abstract fun subFile(displayName: String): UniFile

    /**
     * Deletes this file.
     *
     *
     * Note that this method does *not* throw `IOException` on
     * failure. Callers must check the return value.
     *
     * @return `true` if this file was deleted, `false` otherwise.
     * @see android.provider.DocumentsContract.deleteDocument
     */
    abstract fun delete(): Boolean

    /**
     * Returns a boolean indicating whether this file can be found.
     *
     * @return `true` if this file exists, `false` otherwise.
     */
    abstract fun exists(): Boolean

    abstract fun listFiles(): List<UniFile>

    abstract fun findFirst(filter: (String) -> Boolean): UniFile?

    /**
     * Test there is a file with the display name in the directory.
     *
     * @return the file if found it, or `null`.
     */
    abstract fun findFile(displayName: String): UniFile?

    /**
     * Renames this file to `displayName`.
     *
     *
     * Note that this method does *not* throw `IOException` on
     * failure. Callers must check the return value.
     *
     *
     * Some providers may need to create a new file to reflect the rename,
     * potentially with a different MIME type, so [.getUri] and
     * [.getType] may change to reflect the rename.
     *
     *
     * When renaming a directory, children previously enumerated through
     * [.listFiles] may no longer be valid.
     *
     * @param displayName the new display name.
     * @return true on success.
     * @see android.provider.DocumentsContract.renameDocument
     */
    abstract fun renameTo(displayName: String): Boolean

    @get:RequiresApi(Build.VERSION_CODES.P)
    abstract val imageSource: ImageDecoder.Source

    abstract fun openFileDescriptor(mode: String): ParcelFileDescriptor

    companion object {
        /**
         * Create a [UniFile] representing the given [File].
         *
         * @param file the file to wrap
         * @return the [UniFile] representing the given [File].
         */
        fun fromFile(file: File) = RawFile(null, file)

        /**
         * Create a [UniFile] representing the single document at the
         * given [Uri]. This is only useful on devices running
         * [android.os.Build.VERSION_CODES.KITKAT] or later, and will return
         * `null` when called on earlier platform versions.
         *
         * @param singleUri the [Intent.getData] from a successful
         * [Intent.ACTION_OPEN_DOCUMENT] or
         * [Intent.ACTION_CREATE_DOCUMENT] request.
         * @return the [UniFile] representing the given [Uri].
         */
        fun fromSingleUri(singleUri: Uri) = SingleDocumentFile(null, singleUri)

        /**
         * Create a [UniFile] representing the document tree rooted at
         * the given [Uri]. This is only useful on devices running
         * [Build.VERSION_CODES.LOLLIPOP] or later, and will return
         * `null` when called on earlier platform versions.
         *
         * @param treeUri the [Intent.getData] from a successful
         * [Intent.ACTION_OPEN_DOCUMENT_TREE] request.
         * @return the [UniFile] representing the given [Uri].
         */
        fun fromTreeUri(context: Context, treeUri: Uri): UniFile? {
            val version = Build.VERSION.SDK_INT
            return if (version >= 21) {
                TreeDocumentFile(
                    null,
                    context,
                    DocumentsContractApi21.prepareTreeUri(treeUri),
                )
            } else {
                null
            }
        }

        /**
         * Create a [UniFile] representing the media file rooted at
         * the given [Uri].
         *
         * @param mediaUri the media uri to wrap
         * @return the [UniFile] representing the given [Uri].
         */
        fun fromMediaUri(context: Context, mediaUri: Uri): UniFile {
            return MediaFile(context, mediaUri)
        }

        /**
         * Create a [UniFile] representing the given [Uri].
         */
        fun fromUri(context: Context, uri: Uri): UniFile? {
            return if (isFileUri(uri)) {
                fromFile(File(uri.path!!))
            } else if (isDocumentUri(context, uri)) {
                if (isTreeUri(uri)) {
                    fromTreeUri(context, uri)
                } else {
                    fromSingleUri(uri)
                }
            } else if (MediaFile.isMediaUri(context, uri)) {
                MediaFile(context, uri)
            } else {
                null
            }
        }

        /**
         * Test if given Uri is FileUri
         */
        fun isFileUri(uri: Uri): Boolean {
            return ContentResolver.SCHEME_FILE == uri.scheme
        }

        /**
         * Test if given Uri is backed by a
         * [android.provider.DocumentsProvider].
         */
        fun isDocumentUri(context: Context, uri: Uri): Boolean {
            val version = Build.VERSION.SDK_INT
            return if (version >= 19) {
                DocumentsContractApi19.isDocumentUri(context, uri)
            } else {
                false
            }
        }

        /**
         * Test if given Uri is TreeUri
         */
        fun isTreeUri(uri: Uri): Boolean {
            val paths = uri.pathSegments
            return ContentResolver.SCHEME_CONTENT == uri.scheme && paths.size >= 2 && "tree" == paths[0]
        }
    }
}
