/*
 * Copyright (c) 2021. Linkit and or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can only use it for personal uses, studies or documentation.
 * You can download this source code, and modify it ONLY FOR PERSONAL USE and you
 * ARE NOT ALLOWED to distribute your MODIFIED VERSION.
 *
 * Please contact maximebatista18@gmail.com if you need additional information or have any
 * questions.
 */

package fr.`override`.linkit.api.system.fsa.io

import fr.`override`.linkit.api.system.fsa.{AbstractFileSystemAdapter, FileAdapter}

import java.io.{File, InputStream, OutputStream}

class IOFileSystemAdapter private[fsa]() extends AbstractFileSystemAdapter {
    override val name: String = "java.io"

    override def createAdapter(path: String): FileAdapter = new IOFileAdapter(path, this)

    override def createDirectories(path: FileAdapter): Unit = path.getPath.mkdirs()

    override def create(path: FileAdapter): Unit = path.getPath.createNewFile()

    override def list(path: FileAdapter): Array[FileAdapter] = {
        val files = path.getPath.listFiles()
        if (files == null)
            return Array()
        files.map(getAdapter)
    }

    override def newInputStream(path: FileAdapter): InputStream = path.newInputStream()

    override def newOutputStream(path: FileAdapter): OutputStream = path.newOutputStream()

    override def delete(path: FileAdapter): Unit = path.delete()

    private implicit def toFile(path: String): File = new File(path)

    private def getAdapter(file: File): FileAdapter = super.getAdapter(file.toString)

    override def move(from: FileAdapter, to: FileAdapter): Unit = {
        val bytes = readAllBytes(from)
        if (to notExists) {
            createDirectories(to)
            delete(to)
            create(to)
        }

        val output = to.newOutputStream()
        try output.write(bytes)
        finally {
            output.flush()
            output.close()
            from.delete()
        }

    }
}
