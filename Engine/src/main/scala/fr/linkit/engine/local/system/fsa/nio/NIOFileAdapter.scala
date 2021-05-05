/*
 *  Copyright (c) 2021. Linkit and or its affiliates. All rights reserved.
 *  DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 *  This code is free software; you can only use it for personal uses, studies or documentation.
 *  You can download this source code, and modify it ONLY FOR PERSONAL USE and you
 *  ARE NOT ALLOWED to distribute your MODIFIED VERSION.
 *
 *  Please contact maximebatista18@gmail.com if you need additional information or have any
 *  questions.
 */

package fr.linkit.engine.local.system.fsa.nio

import fr.linkit.api.connection.network.cache.repo.annotations.Hidden
import fr.linkit.api.local.system.fsa.{FileAdapter, FileSystemAdapter}

import java.io.{InputStream, OutputStream}
import java.net.URI
import java.nio.file._

case class NIOFileAdapter private[nio](@Hidden path: Path, @transient fsa: NIOFileSystemAdapter) extends FileAdapter {
    def this(other: NIOFileAdapter) = {
        this(other.path, other.fsa)
    }

    @Hidden
    override def getPath: String = path.toString

    @Hidden
    override def getFSAdapter: FileSystemAdapter = fsa

    @Hidden
    override def getAbsolutePath: String = path.toAbsolutePath.toString

    override def getSize: Long = Files.size(path)

    override def getParent(level: Int): FileAdapter = {
        var parent = path
        for (_ <- 0 to level) {
            parent = parent.getParent
        }
        fsa.getAdapter(parent.toString)
    }

    @Hidden
    override def getName: String = path.getFileName.toString

    @Hidden
    override def getContentString: String = Files.readString(path)

    @Hidden
    override def toUri: URI = path.toUri

    override def resolveSibling(path: String): FileAdapter = resolveSiblings(fsa.getAdapter(path))

    override def resolveSiblings(fa: FileAdapter): FileAdapter = {
        val resolved = path.resolveSibling(path.getParent)
        fsa.getAdapter(resolved.toString)
    }

    override def isDirectory: Boolean = Files.isDirectory(path)

    override def isReadable: Boolean = Files.isReadable(path)

    override def isWritable: Boolean = Files.isWritable(path)

    override def delete(): Boolean = Files.deleteIfExists(path)

    override def exists: Boolean = Files.exists(path)

    override def notExists: Boolean = Files.notExists(path)

    override def createAsFile(): Unit = {
        if (notExists) {
            if (Files.notExists(path.getParent))
                Files.createDirectories(path.getParent)
            Files.createFile(path)
        }
    }

    override def createAsFolder(): Unit = {
        if (notExists) {
            Files.createDirectories(path)
        }
    }

    override def isPresentOnDisk: Boolean = exists

    override def newInputStream(append: Boolean = false): InputStream = {
        Files.newInputStream(path, options(append): _*)
    }

    override def newOutputStream(append: Boolean = false): OutputStream = {
        Files.newOutputStream(path, options(append): _*)
    }

    override def write(bytes: Array[Byte], append: Boolean = false): Unit = {
        Files.write(path, bytes, options(append): _*)
    }

    private def options(append: Boolean): Array[OpenOption] =
        if (append) Array(StandardOpenOption.APPEND) else Array[OpenOption]()
}
