//
//  myServiceDelegate.swift
//  capstone
//
//  Created by Darren Kitamura on 2014-12-23.
//  Copyright (c) 2014 DKitamura. All rights reserved.
//

import Foundation



class myServiceDelegate:NSObject, NSNetServiceBrowserDelegate, NSNetServiceDelegate {
    
    var myServiceResolver = NSNetService()
    var searching:Bool
    var services = [NSNetService]()
   // var serviceArray:NSMutableArray
    
    
    override init() {
        
        self.searching = false

        super.init()
    myServiceResolver.delegate = self
    }
    
    
    //Service found
    func netServiceBrowser(aNetServiceBrowser: NSNetServiceBrowser, didFindService aNetService: NSNetService, moreComing: Bool) {
        self.searching = true
        
        services.append(aNetService)
        println("Found that service and added it to services variable! Total Count:\(services.count)")
        println("\(aNetService)")
        aNetService.delegate = self
        aNetService.resolveWithTimeout(0.0)
        
    }
    
    func netServiceWillResolve(sender: NSNetService) {
        println("Resolved \(sender)")
    }
    
    func netServiceDidResolveAddress(services: NSNetService) {
        myServiceResolver.stop()
        
        for address in services.addresses! {
            
            var inetAddress : sockaddr_in!
            var inetAddress6 : sockaddr_in6!
            //NSData’s bytes returns a read-only pointer to the receiver’s contents.
            var inetAddressPointer = UnsafePointer<sockaddr_in>(address.bytes)
            //Access the underlying raw memory
            inetAddress = inetAddressPointer.memory
            if inetAddress.sin_family == __uint8_t(AF_INET) {
            }
            else {
                if inetAddress.sin_family == __uint8_t(AF_INET6) {
                    var inetAddressPointer6 = UnsafePointer<sockaddr_in6>(address.bytes)
                    inetAddress6 = inetAddressPointer6.memory
                    inetAddress = nil
                }
                else {
                    inetAddress = nil
                }
            }
            var ipString : UnsafePointer<Int8>?
            //static func alloc(num: Int) -> UnsafeMutablePointer
            var ipStringBuffer = UnsafeMutablePointer<Int8>.alloc(Int(INET6_ADDRSTRLEN))
            if inetAddress != nil {
                var addr = inetAddress.sin_addr
                ipString = inet_ntop(Int32(inetAddress.sin_family),
                    &addr,
                    ipStringBuffer,
                    __uint32_t (INET6_ADDRSTRLEN))
            } else {
                if inetAddress6 != nil {
                    var addr = inetAddress6.sin6_addr
                    ipString = inet_ntop(Int32(inetAddress6.sin6_family),
                        &addr,
                        ipStringBuffer,
                        __uint32_t(INET6_ADDRSTRLEN))
                }
            }
            if ipString != nil {
                // Returns `nil` if the `CString` is `NULL` or if it contains ill-formed
                // UTF-8 code unit sequences.
                var ip = String.fromCString(ipString!)
                if ip != nil {
                    NSLog("\(services.name)(\(services.type)) - \(ip!)")
                    var ext : String
                    
                   
                    NSNotificationCenter.defaultCenter().postNotificationName(
                        "NewSharedDetected", object: self)
                }
            }
            ipStringBuffer.dealloc(Int(INET6_ADDRSTRLEN))
        }
    }

}