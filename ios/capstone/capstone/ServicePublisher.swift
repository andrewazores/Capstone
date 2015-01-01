//
//  ServicePublisher.swift
//  capstone
//
//  Created by Darren Kitamura on 2014-12-28.
//  Copyright (c) 2014 DKitamura. All rights reserved.
//

import Foundation

class ServicePublisher: NSObject, NSNetServiceDelegate {
    
    var spPort:Int32
    var spDomain:String
    var spType:String
    var spName:String
    var spService:NSNetService
    
    
    init (domain: String, name: String, type: String) {
        spPort = Int32(arc4random_uniform(500) + 8000)
        spDomain = domain
        spType = type
        spName = name
        spService = NSNetService(domain: spDomain, type: spType, name: spName, port: spPort)
        
        super.init()
        

    }
    
    
    
    func startService() {
         spService.delegate = self       
        spService.publish()
        println("\(spService)")
        
        
    }
}