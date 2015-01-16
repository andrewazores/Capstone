//
//  ViewController.swift
//  capstone
//
//  Created by Darren Kitamura on 2014-12-23.
//  Copyright (c) 2014 DKitamura. All rights reserved.
//

import UIKit
import Foundation

class ViewController: UIViewController {
    let mySensor = SensorManager.sharedInstance
    var bonjourServiceBrowser = NSNetServiceBrowser()
    var myBonjourServiceDelegate = myServiceDelegate.sharedInstance
    var myService = ServicePublisher.sharedInstance
    
    
    
    override func viewDidLoad() {
        super.viewDidLoad()
        
        //If service initialized publish it
        myService.startService()
        myService.startWebServer()

        //Bonjour Service search - Repeat to keep client list up-to-date
        NSTimer.scheduledTimerWithTimeInterval(5, target: self, selector: "searchForServices", userInfo: nil, repeats: true)
        
        // Do any additional setup after loading the view, typically from a nib.
    }

    override func didReceiveMemoryWarning() {
        super.didReceiveMemoryWarning()
        // Dispose of any resources that can be recreated.
    }



    //Search Bonjour service
    func searchForServices() {
        bonjourServiceBrowser.delegate = myBonjourServiceDelegate
        bonjourServiceBrowser.searchForServicesOfType("_http._tcp", inDomain: "local")
        self.listFoundServices()
        self.pingFoundServices()
    }
    
    func listFoundServices() {
        println("\(self.myBonjourServiceDelegate.clientsResolvedList)")
    }
    
    func pingFoundServices() {
        
        for (listIP, listPort) in self.myBonjourServiceDelegate.clientsResolvedList {
            var address = "http://\(listIP):\(listPort)"
            if(listIP as String? != nil) {
                httpGetAddress(address)
            }
        }
    }

   
    func httpGetAddress(address: String) {
        println("HTTP Get address: \(address)")
        var req = Agent.get(address, headers: ["method": "update"])
        req.end({(response: NSHTTPURLResponse!, data: Agent.Data!, error: NSError!)-> Void in
            if(response == nil) {
                println("Remove From List here")
            }
            println("Response: \(response)")
            println("DATA: \(data)")

            
        })
    }
    
    

}