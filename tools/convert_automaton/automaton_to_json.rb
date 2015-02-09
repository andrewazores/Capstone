#!/usr/bin/env ruby

require 'rubygems'
require 'bundler/setup'

require 'oj'

require_relative '../common/AutomatonParts'
require_relative '../common/OptParser'

include OptParser

@options = {}
def convert(input)
  transitions = []
  state_names = []

  transition_pattern = /^([\d]+) -> ([\d]+)(?: \[label="(.*)"\])?$/
  label_pattern = /^([\d]+) \[label="(.*)"(?:, peripheries=(.*))?\]$/
  initial_state_pattern = /^([\d]+) -> ([\d]+)$/

  lines = input.split /\r?\n/
  lines.each do |line|
    line.strip!
    line.gsub! "\\n", ""
    line.gsub! "{Acc[1]}", ""
  end

  # figure out mapping of graphviz IDs to SPOT IDs
  id_map = Hash.new "0"
  lines.each do |line|
    label_pattern.match line do |match|
      gv_id = match[1]
      spot_id = match[2]
      id_map[gv_id] = spot_id unless spot_id.empty?
    end
  end

  # identify transitions
  lines.each do |line|
    transition_pattern.match line do |match|
      src = id_map[match[1]]
      dest = id_map[match[2]]
      predicate = match[3]

      transitions << (Transition.new src, dest, predicate) unless predicate.nil?
    end
  end

  initial = ""
  # identify initial state
  lines.each do |line|
    initial_state_pattern.match line do |match|
      state_names << (StateName.new id_map[match[2]], 'I')
      initial = id_map[match[2]]
    end
  end

  final_states = []
  satisfied_states = []
  # identify accepting states
  lines.each do |line|
    label_pattern.match line do |match|
      next if match[3].nil? or match[3].empty?
      id = id_map[match[1]]
      state_names << (StateName.new id, 'S')
      satisfied_states << id
      final_states << id
    end
  end

  # identify rejecting states
  lines.each do |line|
    transition_pattern.match line do |match|
      id = id_map[match[1]]
      id2 = id_map[match[2]]
      label = match[3]
      next unless id == id2 and label == "1" and not satisfied_states.include? id
      state_names << (StateName.new id, 'V')
      final_states << id
    end
  end

  transitions.each do |edge|
    state_names << (StateName.new edge.source, 'PV') unless final_states.include? edge.source or edge.source == initial or state_names.any? {|s| s.label == edge.source}
    state_names << (StateName.new edge.destination, 'PV') unless final_states.include? edge.destination or edge.destination == initial or state_names.any? {|s| s.label == edge.destination}
  end

  automaton = Automaton.new state_names, transitions
  puts Oj::dump automaton, :indent => @options[:pretty] ? 2 : 0
end

if __FILE__ == $0
  @options = parse_opts
  convert ARGF.read
end
