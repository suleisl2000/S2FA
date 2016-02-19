/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.j2fa

import ModeledType._

import scala.reflect.ClassTag
import scala.io._
import scala.sys.process._

import java.util._
import java.io._
import java.net._
import java.util.LinkedList

import com.amd.aparapi.internal.model.Entrypoint
import com.amd.aparapi.internal.model.ClassModel
import com.amd.aparapi.internal.writer.BlockWriter._
import com.amd.aparapi.internal.writer._
import com.amd.aparapi.internal.writer.ScalaParameter.DIRECTION
import com.amd.aparapi.internal.model.HardCodedClassModels.ShouldNotCallMatcher
import com.amd.aparapi.internal.writer.KernelWriter
import com.amd.aparapi.internal.writer.KernelWriter.WriterAndKernel
import com.amd.aparapi.internal.util.{Utils => AparapiUtils}

import org.apache.spark.blaze.Accelerator
import org.apache.j2fa.AST._

class Kernel(accClazz: Class[_], mInfo: MethodInfo, loader: URLClassLoader) {

  def generate: Option[String] = {

    System.setProperty("com.amd.aparapi.logLevel", Logging.getLevel)
    System.setProperty("com.amd.aparapi.enable.NEW", "true")
    System.setProperty("com.amd.aparapi.enable.INVOKEINTERFACE", "true")

    val mName = mInfo.getName
    val outputMerlin = if (mInfo.getConfig("output_format") == "Merlin") true else false
    var isMapPartitions: Boolean = if (mInfo.getConfig("kernel_type") == "mapPartitions") true else false

    if (isMapPartitions)
      System.setProperty("com.amd.aparapi.kernelType", "MapPartitions")
    else
      System.setProperty("com.amd.aparapi.kernelType", "Map")

    if (outputMerlin)
      System.setProperty("com.amd.aparapi.enable.MERLIN", "true")

    val classModel : ClassModel = ClassModel.createClassModel(accClazz, null, new ShouldNotCallMatcher())

    try {
      // Setup arguments and return values
      val params : LinkedList[ScalaParameter] = new LinkedList[ScalaParameter]
      mInfo.getArgs.foreach({ arg =>
        val param = AparapiUtils.createScalaParameter(
            arg.getFullType, arg.getName, ScalaParameter.DIRECTION.IN)
        params.add(param)
      })
      if (mInfo.hasOutput) {
        val outArg = mInfo.getOutput
        val param = AparapiUtils.createScalaParameter(
            outArg.getFullType, "j2faOut", ScalaParameter.DIRECTION.OUT)
        params.add(param)
      }

      // Identify the kernel method object
      val methods = accClazz.getMethods.filter(m => m.getName.equals(mName))
      var fun: Object = null
      methods.foreach(m => {
        // Transform to signature
        try {
          val des = m.toString.replace(".", "/").split(' ')
          val args = des(2).substring(des(2).indexOf('(') + 1, des(2).indexOf(')')).split(',')
          var sig = "("
          args.foreach(e => sig += Utils.asBytecodeType(e))
          sig += ")" + Utils.asBytecodeType(des(1))
          if (sig.equals(mInfo.getSig))
            fun = m
        } catch {
          case _: Throwable =>
            Logging.warn("Transform method fail: " + m.toString)
        }
      })

      // Create Entrypoint and generate the kernel
      val entryPoint = classModel.getEntrypoint(mName, mInfo.getSig, fun, params, loader)
      val writerAndKernel = KernelWriter.writeToString(entryPoint, params)
      var kernelString = writerAndKernel.kernel
      kernelString = KernelWriter.applyXilinxPatch(kernelString)
      Logging.info("Generate the kernel successfully")
      Some(kernelString)
    } catch {
      case e: Throwable =>
        val sw = new StringWriter
        e.printStackTrace(new PrintWriter(sw))
        val fullMsg = sw.toString
        Logging.severe("Kernel generated failed: " + fullMsg)
        None
    }
  } 
}
