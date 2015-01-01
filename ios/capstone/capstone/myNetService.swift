//
//  myNetService.swift
//  capstone
//
//  Created by Darren Kitamura on 2014-12-24.
//  Copyright (c) 2014 DKitamura. All rights reserved.
//

import Foundation

class myNetService: NSNetService, NSNetServiceDelegate {
    
    override init() {
        println("myNetService Initializd")
        super.init()
    }
    
    
    func netServiceDidResolveAddress(sender: NSNetService) {
        println("Net Service Resolved: ")
        println("Host Name: \(sender.hostName) and Port: \(sender.port)")
    }
}