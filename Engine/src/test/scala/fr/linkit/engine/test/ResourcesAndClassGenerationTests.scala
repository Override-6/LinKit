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

package fr.linkit.engine.test

import fr.linkit.api.connection.cache.repo.Puppeteer
import fr.linkit.api.connection.cache.repo.description.PuppetDescription
import fr.linkit.api.connection.cache.repo.generation.PuppeteerDescription
import fr.linkit.api.local.resource.external.{ResourceFile, ResourceFolder}
import fr.linkit.api.local.system.Version
import fr.linkit.api.local.system.config.ApplicationConfiguration
import fr.linkit.api.local.system.fsa.FileSystemAdapter
import fr.linkit.api.local.system.security.ApplicationSecurityManager
import fr.linkit.engine.connection.cache.repo.SimplePuppeteer
import fr.linkit.engine.connection.cache.repo.generation.{PuppetWrapperClassGenerator, TypeVariableTranslator, WrappersClassResource}
import fr.linkit.engine.local.LinkitApplication
import fr.linkit.engine.local.resource.external.LocalResourceFolder._
import fr.linkit.engine.local.system.fsa.LocalFileSystemAdapters
import fr.linkit.engine.test.ResourcesAndClassGenerationTests.TestClass
import fr.linkit.engine.test.objects.PlayerObject
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation
import org.junit.jupiter.api.TestInstance.Lifecycle
import org.junit.jupiter.api._
import org.mockito.Mockito

import java.util
import scala.collection.mutable.ListBuffer

@TestInstance(Lifecycle.PER_CLASS)
@TestMethodOrder(classOf[OrderAnnotation])
class ResourcesAndClassGenerationTests {

    private var resources: ResourceFolder = _

    @BeforeAll
    def init(): Unit = {
        val config = new ApplicationConfiguration {
            override val pluginFolder   : Option[String]             = None
            override val resourceFolder : String                     = "C:\\Users\\maxim\\Desktop\\Dev\\Linkit\\Home"
            override val fsAdapter      : FileSystemAdapter          = LocalFileSystemAdapters.Nio
            override val securityManager: ApplicationSecurityManager = null
        }
        System.setProperty("LinkitImplementationVersion", Version("Tests", "0.0.0", false).toString)

        LinkitApplication.mapEnvironment(LocalFileSystemAdapters.Nio, Seq(getClass))
        resources = LinkitApplication.prepareAppResources(config)
    }


    @Test
    @Order(-1)
    def genericParameterTests(): Unit = {
        val testMethod = classOf[TestClass].getMethod("genericMethod2")
        val javaResult = TypeVariableTranslator.toJavaDeclaration(testMethod.getTypeParameters)
        val scalaResult = TypeVariableTranslator.toScalaDeclaration(testMethod.getTypeParameters)
         println(s"Java result = ${javaResult}")
        println(s"Scala result = ${scalaResult}")
    }

    @Test
    @Order(0)
    def methodsSignatureTests(): Unit = {
        val clazz = classOf[TestClass]
        println(s"clazz = ${clazz}")
        val methods = clazz.getDeclaredMethods
        methods.foreach { method =>
            val args = method.getGenericParameterTypes
            println(s"args = ${args.mkString("Array(", ", ", ")")}")
        }
        println("qsd")
    }

    @Test
    @Order(2)
    def generateSimpleClass(): Unit = {
        forObject(PlayerObject(7, "sheeeesh", "slt", 1, 5))
    }

    @Test
    @Order(3)
    def generateComplexScalaClass(): Unit = {
       forObject(ListBuffer.empty[String])
    }

    @Test
    @Order(3)
    def generateComplexJavaClass(): Unit = {
        forObject(new util.ArrayList[String]())
    }

    private def forObject(obj: Any): Unit = {
        Assertions.assertNotNull(resources)
        val cl = obj.getClass.asInstanceOf[Class[obj.type]]

        val resource    = resources.getOrOpenShort[WrappersClassResource]("PuppetGeneration")
        val generator   = new PuppetWrapperClassGenerator(resource)
        val puppetClass = generator.getClass(cl)
        println(s"puppetClass = ${puppetClass}")
        val pup    = new SimplePuppeteer[obj.type](null, null, PuppeteerDescription("", 8, "", Array(1)), PuppetDescription[obj.type](cl))
        val puppet = puppetClass.getDeclaredConstructor(classOf[Puppeteer[_]], cl).newInstance(pup, obj)
        println(s"puppet = ${puppet.detachedSnapshot()}")
    }

}

object ResourcesAndClassGenerationTests {
    class TestClass {

        def genericMethod2[EFFE <: ResourceFolder, S <: ResourceFile, V, G, TS >: V, F <: List[(EFFE, S)]]: EFFE = ???

        def genericMethod[EFFE >: ResourceFolder, I >: ResourceFolder]: EFFE = null
    }
}