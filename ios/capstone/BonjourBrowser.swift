//
//  BonjourBrowser.swift
//  capstone
//
//  Created by Darren Kitamura on 2014-12-23.
//  Copyright (c) 2014 DKitamura. All rights reserved.
//

import Foundation

public enum ServiceType {
    case Afp
    case Smb
    case AfpAndSmb
}

class BonjourBrowser:NSObject, NSNetServiceBrowserDelegate, NSNetServiceDelegate
{
    let afpType:String
    let smbType:String
    var smbBrowser:NSNetServiceBrowser
    var afpBrowser:NSNetServiceBrowser
    var serviceList:[NSNetService]
    
    override init() {
        self.afpType = "_afpovertcp._tcp."
        self.smbType = "_smb._tcp."
        self.smbBrowser = NSNetServiceBrowser()
        self.afpBrowser = NSNetServiceBrowser()
        self.serviceList = [NSNetService]()
        super.init()
        
        self.afpBrowser.delegate = self
        self.smbBrowser.delegate = self
    }
    
    
    func netServiceBrowser(aNetServiceBrowser: NSNetServiceBrowser, didFindService aNetService: NSNetService, moreComing: Bool) {
        serviceList.append(aNetService)
        NSLog("Found: \(aNetService)")
        if !moreComing {
            update()
        }
    }
    
    func netServiceBrowser(aNetServiceBrowser: NSNetServiceBrowser, didNotSearch errorDict: [NSObject : AnyObject]) {
        NSLog("Search was not successful. Error code: \(errorDict[NSNetServicesErrorCode])")
    }
    
    func netServiceBrowser(aNetServiceBrowser: NSNetServiceBrowser, didRemoveService aNetService: NSNetService, moreComing: Bool) {
        let iService = find(serviceList, aNetService)
        if iService != nil {
            serviceList.removeAtIndex(iService!)
        }
        NSLog("Became unavailable: \(aNetService)")
        if !moreComing {
            update()
        }
    }
    
    func update() {
        for service in serviceList {
            service.delegate = self
            service.resolveWithTimeout(5)
        }
    }
    
    func netServiceDidResolveAddress(sender: NSNetService) {
        //service.addresses - array containing NSData objects, each of which contains an appropriate
        //sockaddr structure that you can use to connect to the socket
        for addressBytes in sender.addresses!
        {
            var inetAddress : sockaddr_in!
            var inetAddress6 : sockaddr_in6?
            //NSData’s bytes returns a read-only pointer (const void *) to the receiver’s contents.
            //var bytes: UnsafePointer<()> { get }
            var inetAddressPointer = UnsafePointer<sockaddr_in>(addressBytes.bytes)
            //Access the underlying raw memory
            inetAddress = inetAddressPointer.memory
            
            if inetAddress.sin_family == __uint8_t(AF_INET) //Note: explicit convertion (var AF_INET: Int32 { get } /* internetwork: UDP, TCP, etc. */)
            {
            }
            else
            {
                if inetAddress.sin_family == __uint8_t(AF_INET6) //var AF_INET6: Int32 { get } /* IPv6 */
                {
                    var inetAddressPointer6 = UnsafePointer<sockaddr_in6>(addressBytes.bytes)
                    inetAddress6 = inetAddressPointer6.memory
                    inetAddress = nil
                }
                else
                {
                    inetAddress = nil
                }
            }
            var ipString : UnsafePointer<Int8>?
            //static func alloc(num: Int) -> UnsafeMutablePointer<T>
            var ipStringBuffer = UnsafeMutablePointer<Int8>.alloc(Int(INET6_ADDRSTRLEN))
            if (inetAddress != nil)
            {
                var addr = inetAddress.sin_addr
                ///func inet_ntop(_: Int32, _: UnsafePointer<()>, _: UnsafeMutablePointer<Int8>, _: socklen_t) -> UnsafePointer<Int8>
                ipString = inet_ntop(Int32(inetAddress.sin_family),
                    &addr,
                    ipStringBuffer,
                    __uint32_t(INET6_ADDRSTRLEN))
            }
            else
            {
                if (inetAddress6 != nil)
                {
                    var addr  = inetAddress6!.sin6_addr
                    
                    ipString = inet_ntop(Int32(inetAddress6!.sin6_family),
                        &addr,
                        ipStringBuffer,
                        __uint32_t(INET6_ADDRSTRLEN))
                }
            }
            if (ipString != nil)
            {
                // Returns `nil` if the `CString` is `NULL` or if it contains ill-formed
                // UTF-8 code unit sequences.
                //static func fromCString(cs: UnsafePointer<CChar>) -> String?
                var ip = String.fromCString(ipString!)
                if ip != nil {
                    NSLog("\(sender.name)(\(sender.type)) - \(ip!)")
                    var ext : String
                    switch sender.type {
                    case afpType :
                        ext = "afp"
                    case smbType :
                        ext = "smb"
                    default:
                        ext = sender.type
                    }
                    
                    NSNotificationCenter.defaultCenter().postNotificationName("NewSharedDetected", object: self)
                }
            }
            /// This type stores a pointer to an object of type T. It provides no
            /// automated memory management, and therefore the user must take care
            /// to allocate and free memory appropriately.
            ipStringBuffer.dealloc(Int(INET6_ADDRSTRLEN))
        }
    }
    
    func netService(sender: NSNetService, didNotResolve errorDict: [NSObject : AnyObject]) {
        NSLog("\(sender.name) did not resolve: \(errorDict[NSNetServicesErrorCode]!)")
    }
    
    func searchForServicesOfType(type:ServiceType) {
        switch type {
        case .Afp:
            afpBrowser.searchForServicesOfType(afpType, inDomain: "")
        case .Smb:
            smbBrowser.searchForServicesOfType(smbType, inDomain: "")
        case .AfpAndSmb:
            afpBrowser.searchForServicesOfType(afpType, inDomain: "")
            smbBrowser.searchForServicesOfType(smbType, inDomain: "")
        }
    }
    
    func reset() {
        smbBrowser.stop()
        afpBrowser.stop()
        for service in serviceList {
            service.stop()
        }
        serviceList.removeAll()
    }
}


