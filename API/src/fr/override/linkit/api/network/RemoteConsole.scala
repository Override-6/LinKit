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

package fr.`override`.linkit.api.network

import fr.`override`.linkit.api.packet.fundamental.PairPacket
import fr.`override`.linkit.api.packet.traffic.channel.AsyncPacketChannel
import fr.`override`.linkit.api.utils.InactiveOutputStream
import sun.security.action.GetPropertyAction

import java.io.PrintStream
import java.security.AccessController


class RemoteConsole private(@Nullable channel: AsyncPacketChannel,
                            kind: String) extends PrintStream(InactiveOutputStream, true) {

    override def write(b: Array[Byte]): Unit = {
        print(new String(b))
    }

    override def write(b: Int): Unit = {
        print(b)
    }

    override def write(buf: Array[Byte], off: Int, len: Int): Unit = {
        print(new String(buf.slice(off, off + len)))
    }

    override def print(obj: Any): Unit = {
        var str = String.valueOf(obj)

        if (obj.getClass.isArray)
            str = java.util.Arrays.deepToString(obj.asInstanceOf[Array[AnyRef]])

        if (channel != null)
            channel.send(PairPacket(kind, String.valueOf(obj))) //prints to the linked console
    }

    override def print(x: Boolean): Unit = print(x: Any)

    override def print(c: Char): Unit = {
        print(c: Any)
    }

    override def print(s: Array[Char]): Unit = print(s: Any)

    //avoid newLine
    override def print(str: String): Unit = if (str != lineSeparator) print(str: Any)

    override def print(i: Int): Unit = print(i: Any)

    override def print(l: Long): Unit = print(l: Any)

    override def print(f: Float): Unit = print(f: Any)

    override def print(d: Double): Unit = print(d: Any)

    private val lineSeparator = AccessController.doPrivileged(new GetPropertyAction("line.separator"))

}

object RemoteConsole {

    def err(channel: AsyncPacketChannel): RemoteConsole = new RemoteConsole(channel, "err")

    def out(channel: AsyncPacketChannel): RemoteConsole = new RemoteConsole(channel, "out")

    val Mock: RemoteConsole = new RemoteConsole(null, "mock")

}