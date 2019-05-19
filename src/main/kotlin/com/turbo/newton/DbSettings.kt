//package com.turbo.newton
//
//import org.jetbrains.exposed.sql.Database
//
//object DbSettings {
//  private var url: String? = null
//  private var user: String? = null
//  private var password: String? = null
//  private var driver: String? = null
//
//  fun init(url: String, user: String, password: String, driver: String) {
//    this.url = url
//    this.user = user
//    this.password = password
//    this.driver = driver
//  }
//  val db by lazy {
//    Database.connect(
//        url = url!!,
//        user = user!!,
//        password = password!!,
//        driver = driver!!
//    )
//  }
//}
