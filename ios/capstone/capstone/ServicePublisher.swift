//
//  ServicePublisher.swift
//  capstone
//
//  Created by Darren Kitamura on 2014-12-28.
//  Copyright (c) 2014 DKitamura. All rights reserved.
//

import UIKit
import Foundation

class ServicePublisher: NSObject, NSNetServiceDelegate {
    
   
    
    class var sharedInstance: ServicePublisher {
        
        struct Static {
            static var instance: ServicePublisher?
            static var token: dispatch_once_t = 0
        }
        
        dispatch_once(&Static.token) {
            Static.instance = ServicePublisher(domain: "local", name: "CapstoneLocationNSD-", type: "_http._tcp")
        }
        return Static.instance!
    }

    
    var spPort:Int32
    var spDomain:String
    var spType:String
    var spName:String
    var spService:NSNetService
    
    let UUID: String
    var UUIDChunk = [String]()
    let myHTTPServer = HttpServer()
    let sensorManager: SensorManager

    
    
    init (domain: String, name: String, type: String) {
        spPort = Int32(arc4random_uniform(500) + 8000)
        spDomain = domain
        spType = type
        spName = name
        UUID = NSUUID().UUIDString
        UUIDChunk = UUID.componentsSeparatedByString("-")
        spService = NSNetService(domain: spDomain, type: spType, name: spName+UUIDChunk[0], port: spPort)
        sensorManager = SensorManager.sharedInstance
        println("ServicePublisher Initalized called: \(spName+UUIDChunk[0]) running on port: \(spPort)")
        super.init()
        

    }
    
    func singletonTest() {
        println("Test called: \(spName+UUIDChunk[0]) running on port: \(spPort)")
        
    }
    
    func getServiceName() -> String {
        return spName+UUIDChunk[0]
    }
    
    func startService() {
         spService.delegate = self       
        spService.publish()
        println("service published")
        
    }
    
    
    func startWebServer() {
        //Start HTTP Server

        self.myHTTPServer["/hello"] = {
            
            request in
            var test = self.sensorManager.getAccelerationData()
            println("\(test)")
            return {
                .OK(.JSON(["rotationX": self.sensorManager.rotX]))
                }()
        }
        self.myHTTPServer.start(listenPort: UInt16(spPort), error:nil)
    }
}