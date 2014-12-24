#!/usr/bin/env ruby

# See here for an explanation of the automaton format.
# https://app.asana.com/0/16128900311154/22979367142779

require 'rubygems'
require 'oj'
require 'optparse'

class String
  def word_wrap(width = 120)
    source = self.dup
    original_width = width
    while width < source.length do
      last_space = source.rindex(/ |\W/, width)
      source.insert last_space, "\n"
      source.gsub! /\n */, "\n"
      width = last_space + original_width
    end
    source
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

  opt.on('-v', '--verbose', 'verbose (debugging/verification) output') do
    options[:verbose] = true
  end
end
opt_parser.parse!

module JsonStringify
  def to_s
    Oj::dump self
  end
end

class Automaton
  include JsonStringify
  attr_reader :state_names, :transitions
  def initialize(state_names, transitions)
    @state_names = state_names
    @transitions = transitions
  end

end

class StateName
  include JsonStringify
  attr_reader :label, :type
  def initialize(label, type)
    @label = label
    @type = case type
              when 'I' then 'initial'
              when 'PV' then 'partial'
              when 'A' then 'accept'
              when 'R' then 'reject'
              else 'unknown'
            end
  end
end

class Transition
  include JsonStringify
  attr_reader :source, :destination, :predicate
  def initialize(source, destination, predicate)
    @source = source
    @destination = destination
    @predicate = predicate
  end
end

class Verifier
  def initialize(automaton, verbose=false)
    @automaton = automaton
    @verbose = verbose
  end
  def verify
    verification = {:valid => true, :messages => []}
    labels = @automaton.state_names.map &:label
    verification[:messages] << "#labels: #{labels}".word_wrap if @verbose
    @automaton.transitions.each do |t|
      unless labels.include? t.source
        verification[:messages] << "State label #{t.source} is not in the automaton states but is in the transition #{t}".word_wrap
        verification[:valid] = false
      end
      unless labels.include? t.destination
        verification[:messages] << "State label #{t.destination} is not in the automaton states but is in the transition #{t}".word_wrap
        verification[:valid] = false
      end
    end

    states = @automaton.state_names.map &:type
    verification[:messages] <<  "#states: #{states}".word_wrap if @verbose
    @automaton.state_names.each do |s|
      if s.type == :unknown
        verification[:messages] << "Transition #{s} has unknown type".word_wrap
        verification[:valid] = false
      end
    end

    connected_nodes = @automaton.transitions.map(&:source) | @automaton.transitions.map(&:destination)
    verification[:messages] <<  "#connected nodes: #{connected_nodes}".word_wrap if @verbose
    @automaton.state_names.map(&:label).each do |s|
      unless connected_nodes.include? s
        verification[:messages] << "State #{s} is named as a state in the automaton but is not connected in the graph".word_wrap
        verification[:valid] = false
      end
    end

    verification
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
  unless state =~ /^\w+,\w+$/
    puts "Could not parse state name line: '#{state}'"
    exit 1
  end
  fields = state.split ','
  label = fields[0]
  type = fields[1]
  state_names << StateName.new(label, type)
end

transitions = []
raw_transitions.each do |line|
  unless line =~ /^\w+,\w+,\w+/
    puts "Could not parse transition line: '#{line}'"
    exit 1
  end
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
verification = Verifier.new(automaton, options[:verbose]).verify
if verification[:valid]
  indent = options[:pretty] ? 2 : 0
  puts Oj::dump automaton, :indent => indent
  puts verification[:messages] if options[:verbose]
  exit 0
else
  puts 'Invalid automaton! :('
  puts "Total errors: #{verification[:messages].size}\n\n"
  verification[:messages].each_with_index do |item, index|
    puts "#{index + 1}: #{item}\n\n"
  end
  exit 1
end
