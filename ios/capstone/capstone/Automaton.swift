//
//  Automaton.swift
//  capstone
//
//  Created by Darren Kitamura on 2015-01-15.
//  Copyright (c) 2015 DKitamura. All rights reserved.
//

import Foundation


class Automaton {
    
    enum Evaluation {
        case SATISIFIED
        case VIOLATED
        case UNDECIDED
    }
    
    private var initialState: AutomatonState
    private var states = Dictionary<String, AutomatonState>()
    private var transitions = NSMutableSet()
    
    //Finish class
}