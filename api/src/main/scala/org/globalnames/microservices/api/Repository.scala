package org.globalnames
package microservices
package api

import javax.inject.Inject

import org.globalnames.microservices.index.thriftscala.IndexService

class Repository @Inject() (indexClient: IndexService.FutureIface) {

}
