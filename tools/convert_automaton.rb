#!/usr/bin/env ruby

require 'rubygems'
require 'oj'

# TEST_STRING = %q{2
# q0,I
# q2,PV
# 19
# q0,q0,p_0_goingwest',-1,
# q0,q0,p_1_goingwest&p_2_goingwest&p_3_goingwest&p_4_goingwest&p_5_goingwest&p_6_goingwest&p_7_goingwest&p_8_goingwest,-1,
# q0,q2,p_0_goingwest&p_1_goingwest',-1,
# q0,q2,p_0_goingwest&p_2_goingwest',-1,
# q0,q2,p_0_goingwest&p_3_goingwest',-1,
# q0,q2,p_0_goingwest&p_4_goingwest',-1,
# q0,q2,p_0_goingwest&p_5_goingwest',-1,
# q0,q2,p_0_goingwest&p_6_goingwest',-1,
# q0,q2,p_0_goingwest&p_7_goingwest',-1,
# q0,q2,p_0_goingwest&p_8_goingwest',-1,
# q2,q0,p_1_goingwest&p_2_goingwest&p_3_goingwest&p_4_goingwest&p_5_goingwest&p_6_goingwest&p_7_goingwest&p_8_goingwest,-1,
# q2,q2,p_1_goingwest',-1,
# q2,q2,p_2_goingwest',-1,
# q2,q2,p_3_goingwest',-1,
# q2,q2,p_4_goingwest',-1,
# q2,q2,p_5_goingwest',-1,
# q2,q2,p_6_goingwest',-1,
# q2,q2,p_7_goingwest',-1,
# q2,q2,p_8_goingwest',-1,
# }

class Automaton
  private
    @state_names = []
    @transitions = []
  public
  def initialize(state_names, transitions)
    @state_names = state_names
    @transitions = transitions
  end
end

class Transition
  private
    @source = ''
    @destination = ''
    @predicate = ''
  public
  def initialize(source, destination, predicate)
    @source = source
    @destination = destination
    @predicate = predicate
  end
end

# lines = TEST_STRING.lines.map &:chomp
lines = ARGF.read.split /\r?\n/

lines.map &:chomp
lines = lines.delete_if do |l|
  l.start_with? '#'
end

num_states = lines[0]
lines = lines.drop 1
meta = lines.take num_states.to_i + 1
raw_transitions = lines.drop num_states.to_i + 1

state_names = meta.take meta.length - 1
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