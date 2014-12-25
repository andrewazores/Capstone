#!/usr/bin/env ruby

# See here for an explanation of the automaton format.
# https://app.asana.com/0/16128900311154/22979367142779

require 'rubygems'
require 'bundler/setup'

require 'oj'

require_relative '../common/StringWordWrap'
require_relative '../common/AutomatonParts'
require_relative '../common/OptParser'

class FileFormatVerifier
  private
    @@num_states_or_transitions_pattern = /^\d+$/
    @@state_name_pattern = /^\w+,\w+$/
    @@transition_pattern = /^\w+,\w+,\w+/
  public
  def initialize(lines, verbose=false)
    @lines = lines
    @verbose = verbose
  end
  def verify
    verification = {:valid => true, :messages => [], :verbose_messages => []}
    num_states = 0
    if @lines[0].chomp =~ @@num_states_or_transitions_pattern
      num_states = @lines[0].chomp.to_i
    else
      verification[:messages] << "Expected first line to contain a number of states, but got: #{@lines[0]}"
      verification[:valid] = false
    end
    if num_states > @lines.length
      verification[:messages] << "Number of states specified (#{num_states}) exceeds total length of input (#{@lines.length})"
      verification[:valid] = false
    end
    num_transitions = 0
    if num_states <= @lines.length and @lines[num_states + 1].chomp =~ @@num_states_or_transitions_pattern
      num_transitions = @lines[num_states + 1].chomp.to_i
    else
      verification[:messages] << "Expected line #{num_states + 1} to contain a number of transitions, but got: #{@lines[num_states + 1]}"
      verification[:valid] = false
    end
    if num_transitions > @lines.length - num_states - 2
      verification[:messages] << "Number of transitions specified (#{num_transitions}) exceeds remaining length of input (#{@lines.length - num_states - 2})"
      verification[:valid] = false
    end
    if num_transitions < @lines.length - num_states - 2
      verification[:messages] << "Number of transitions specified (#{num_transitions}) is less than remaining length of input (#{@lines.length - num_states - 2})"
      verification[:valid] = false
    end
    @lines[1 .. num_states].each_with_index do |line, index|
      unless line =~ @@state_name_pattern
        verification[:messages] << "Expected line #{index} to be a state name, got '#{line}'"
        verification[:valid] = false
      end
    end
    @lines[num_states + 2 .. num_transitions].each_with_index do |line, index|
      unless line =~ @@transition_pattern
        verification[:messages] << "Expected line #{index} to be a transition, got '#{line}'"
        verification[:valid] = false
      end
    end
    if @verbose
      @lines.each_with_index do |line, index|
        line_type = if line =~ @@num_states_or_transitions_pattern
                      '#'
                    elsif line =~ @@state_name_pattern
                      'S'
                    elsif line =~ @@transition_pattern
                      'T'
                    else
                      '?'
                    end
        verification[:verbose_messages] << "#{index + 1}\t#{line_type}: #{line}"
      end
    end
    verification
  end
end

class AutomatonVerifier
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

class ConvertAutomaton
  include OptParser
  def initialize
    @options = parse_opts
  end

  def convert(raw, options=@options)
    input = raw.split /\r?\n/

    input.map &:chomp
    lines = input.delete_if do |l|
      l.start_with? '#'
    end

    file_verification = FileFormatVerifier.new(lines, options[:verbose]).verify
    file_verification[:verbose_messages].each { |m| STDERR.puts m } if options[:verbose]
    if (not file_verification[:valid]) or options[:verbose]
      STDERR.puts 'Invalid file format :(' unless file_verification[:valid]
      STDERR.puts "Total errors: #{file_verification[:messages].length}\n\n"
      file_verification[:messages].each_with_index do |message, index|
        STDERR.puts "#{index + 1}: #{message}\n\n"
      end
      exit 1 unless file_verification[:valid]
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
    automaton_verification = AutomatonVerifier.new(automaton, options[:verbose]).verify
    if automaton_verification[:valid]
      indent = options[:pretty] ? 2 : 0
      puts Oj::dump automaton, :indent => indent
      puts automaton_verification[:messages] if options[:verbose]
      exit 0
    else
      STDERR.puts 'Invalid automaton! :('
      STDERR.puts "Total errors: #{automaton_verification[:messages].size}\n\n"
      automaton_verification[:messages].each_with_index do |item, index|
        STDERR.puts "#{index + 1}: #{item}\n\n"
      end
      exit 2
    end
  end
end

if __FILE__ == $0
  ConvertAutomaton.new.convert ARGF.read
end