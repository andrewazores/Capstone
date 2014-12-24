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
    @num_states = 0
    @state_names = []
    @num_transitions = 0
    @transitions = []
  public
  def initialize(num_states, state_names, num_transitions, transitions)
    @num_states = num_states
    @state_names = state_names
    @num_transitions = num_transitions
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
lines = ARGF.read.split

meta = lines.take 3
raw_transitions = lines.drop 4

num_states = meta[0]
state_names = meta[1]
num_transitions = meta[2]
transitions = []

raw_transitions.each do |line|
  fields = line.split ','
  fields.map &:chomp
  fields = fields.delete_if do |f|
    f == '-1' or f.start_with? '#' or f.empty?
  end
  source = fields[0]
  destination = fields[1]
  predicate = fields[2]
  transitions << Transition.new(source, destination, predicate)
end

automaton = Automaton.new(num_states, state_names, num_transitions, transitions)
puts Oj::dump automaton, :indent => 2