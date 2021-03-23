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

package fr.`override`.linkit.server.config

import fr.`override`.linkit.api.local.system.config.ApplicationConfiguration
import fr.`override`.linkit.api.local.system.config.schematic.{AppSchematic, EmptySchematic}
import fr.`override`.linkit.server.ServerApplication
import org.jetbrains.annotations.NotNull

trait ServerApplicationConfiguration extends ApplicationConfiguration {

    val mainPoolThreadCount: Int

    @NotNull var loadSchematic: AppSchematic[ServerApplication]

}
