package org.globalnames
package index
package util

import thrift.Uuid
import java.util.UUID

import scala.language.implicitConversions

object UuidEnhanced {

  implicit def javaUuid2thriftUuid(uuid: UUID): Uuid = {
    Uuid(uuid.getLeastSignificantBits, uuid.getMostSignificantBits)
  }

  implicit def thriftUuid2javaUuid(uuid: Uuid): UUID = {
    new UUID(uuid.mostSignificantBits, uuid.leastSignificantBits)
  }

  implicit class ThriftUuidEnhanced(val uuid: Uuid) extends AnyVal {
    def string: String = thriftUuid2javaUuid(uuid).toString
  }
}
