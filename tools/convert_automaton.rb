#!/usr/bin/env ruby

# See here for an explanation of the automaton format.
# https://app.asana.com/0/16128900311154/22979367142779

require 'rubygems'
require 'oj'
require 'optparse'

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

options = {}
opt_parser = OptionParser.new do |opt|
  opt.banner = "Usage: #{$0} [OPTIONS]"
  opt.separator ''
  opt.separator 'OPTIONS'

  opt.on('-p', '--pretty', 'pretty print output') do
    options[:pretty] = true
  end
end
opt_parser.parse!

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
  label = fields[0]
  type = fields[1]
  state_names << StateName.new(label, type)
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
indent = options[:pretty] ? 2 : 0
puts Oj::dump automaton, :indent => indent