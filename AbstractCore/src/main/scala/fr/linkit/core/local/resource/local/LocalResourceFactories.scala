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

package fr.linkit.core.local.resource.local

import fr.linkit.api.local.resource.external.{ExternalResource, ExternalResourceFactory, ResourceFile, ResourceFolder}

object LocalResourceFactories {

    def adaptive: ExternalResourceFactory[ExternalResource] = (adapter, listener, parent) => {
        if (adapter.isDirectory) folder(adapter, listener, parent)
        else file(adapter, listener, parent)
    }

    def folder: ExternalResourceFactory[ResourceFolder] = LocalResourceFolder.apply

    def file: ExternalResourceFactory[ResourceFile] = (adapter, _, parent) => {
        LocalResourceFile(parent, adapter)
    }

}