package fr.linkit.api.connection.cache.repo.generation

import fr.linkit.api.connection.cache.repo.PuppetWrapper
import fr.linkit.api.connection.cache.repo.description.PuppetDescription

import scala.reflect.runtime.universe
import scala.reflect.{ClassTag, classTag}

/**
 * This class generates a class that extends
 * */
trait PuppetWrapperGenerator {

    def getPuppetClass[S](clazz: Class[S]): Class[S with PuppetWrapper[S]]

    def getPuppetClass[S](desc: PuppetDescription[S]): Class[S with PuppetWrapper[S]]

    def getClass[S: universe.TypeTag : ClassTag]: Class[S with PuppetWrapper[S]] = getPuppetClass[S](classTag[S].runtimeClass.asInstanceOf[Class[S]])

    def preGenerateDescs[S](descriptions: Seq[PuppetDescription[S]]): Unit

    def preGenerateClasses[S: universe.TypeTag](classes: Seq[Class[_ <: S]]): Unit

    def isClassGenerated[T: ClassTag]: Boolean = isWrapperClassGenerated(classTag[T].runtimeClass)

    def isWrapperClassGenerated[T](clazz: Class[T]): Boolean

    def isClassGenerated[S <: PuppetWrapper[S]](clazz: Class[S]): Boolean
}
