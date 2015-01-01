//
//  bonjourService.swift
//  capstone
//
//  Created by Darren Kitamura on 2014-12-23.
//  Copyright (c) 2014 DKitamura. All rights reserved.
//

import Foundation

class BonjourManager {
    
    var netService:NSNetService
    var type:String
    var ipAddress:String
    
    
    init(netService:NSNetService, type: String, ipAddress:string) {
        self.netService = netService
        self.type = type
        self.ipAddress = ipAddress
    }
}