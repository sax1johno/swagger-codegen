package com.wordnik.client

import com.wordnik.swagger.core.util.JsonUtil
import com.sun.jersey.api.client.Client
import com.sun.jersey.api.client.ClientResponse
import com.sun.jersey.api.client.config.ClientConfig
import com.sun.jersey.api.client.config.DefaultClientConfig
import com.sun.jersey.api.client.filter.LoggingFilter

import java.net.URLEncoder
import javax.ws.rs.core.MediaType

import scala.collection.JavaConverters._
import scala.collection.mutable.HashMap

object ApiInvoker {
  val defaultHeaders: HashMap[String, String] = HashMap()
  val hostMap: HashMap[String, Client] = HashMap()

  def escapeString(value: String): String = {
    URLEncoder.encode(value, "utf-8").replaceAll("\\+", "%20")
  }

  def deserialize(json: String, containerType: String, cls: Class[_]) = {
    if (cls == classOf[String]) {
      json
    } else {
      containerType match {
        case "List" => {
          val typeInfo = org.codehaus.jackson.map.`type`.TypeFactory.collectionType(classOf[java.util.List[_]], cls)
          val response = JsonUtil.getJsonMapper.readValue(json, typeInfo).asInstanceOf[java.util.List[_]]
          response.asScala.toList
        }
        case _ => JsonUtil.getJsonMapper.readValue(json, cls)
      }
    }
  }

  def serialize(obj: AnyRef): String = {
    if (obj != null) {
      obj match {
        case e:List[_] => JsonUtil.getJsonMapper.writeValueAsString(obj.asInstanceOf[List[_]].asJava)
        case _ => JsonUtil.getJsonMapper.writeValueAsString(obj)
      }
    }
    else null
  }

  def invokeApi(host: String, path: String, method: String, queryParams: Map[String, String], body: AnyRef, headerParams: Map[String, String]) = {
    val client = getClient(host)

    val querystring = queryParams.filter(k => k._2 != null).map(k => (escapeString(k._1) + "=" + escapeString(k._2))).mkString("?", "&", "")
    val builder = client.resource(host + path + querystring).`type`("application/json")

    headerParams.map(p => builder.header(p._1, p._2))
    defaultHeaders.map(p => {
      headerParams.contains(p._1) match {
        case true => // override default with supplied header
        case false => if (p._2 != null) builder.header(p._1, p._2)
      }
    })

    val response: ClientResponse = method match {
      case "GET" => {
        builder.get(classOf[ClientResponse]).asInstanceOf[ClientResponse]
      }
      case "POST" => {
        builder.post(classOf[ClientResponse], serialize(body))
      }
      case "PUT" => {
        builder.put(classOf[ClientResponse], serialize(body))
      }
      case "DELETE" => {
        builder.delete(classOf[ClientResponse])
      }
      case _ => null
    }
    response.getClientResponseStatus() match {
      case ClientResponse.Status.OK => response.getEntity(classOf[String])
      case _ => {
        throw new ApiException(
          response.getClientResponseStatus().getStatusCode(),
          response.getEntity(classOf[String]))
      }
    }
  }

  def getClient(host: String): Client = {
    hostMap.contains(host) match {
      case true => hostMap(host)
      case false => {
        val client = Client.create()
        // client.addFilter(new LoggingFilter())
        hostMap += host -> client
        client
      }
    }
  }
}

class ApiException extends Exception {
  var code = 0

  def this(code: Int, msg: String) = {
    this()
  }
}