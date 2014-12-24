#!/usr/bin/env ruby

require 'rubygems'
require 'oj'

class Automaton
  def initialize(state_names, transitions)
    @state_names = state_names
    @transitions = transitions
  end
end

class StateName
  def initialize(label, type)
    @label = label
    @type = case type
              when 'I' then :initial
              when 'PV' then :partial
              when 'A' then :accept
              when 'R' then :reject
              else :unknown
            end
  end
end

class Transition
  def initialize(source, destination, predicate)
    @source = source
    @destination = destination
    @predicate = predicate
  end
end

lines = ARGF.read.split /\r?\n/

lines.map &:chomp
lines = lines.delete_if do |l|
  l.start_with? '#'
end

num_states = lines[0]
lines = lines.drop 1
meta = lines.take num_states.to_i + 1
raw_transitions = lines.drop num_states.to_i + 1

raw_state_names = meta.take meta.length - 1
state_names = []
raw_state_names.each do |state|
  fields = state.split ','
  state_names << StateName.new(fields[0], fields[1])
end

transitions = []
raw_transitions.each do |line|
  fields = line.split ','
  fields.map &:chomp
  fields = fields.delete_if do |f|
    f == '-1' or f.empty?
  end
  source = fields[0]
  destination = fields[1]
  predicate = fields[2]
  transitions << Transition.new(source, destination, predicate)
end

automaton = Automaton.new(state_names, transitions)
puts Oj::dump automaton, :indent => 2