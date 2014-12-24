require_relative './JsonStringify'

class Automaton < JsonStringify
  attr_reader :state_names, :transitions
  def initialize(state_names, transitions)
    @state_names = state_names
    @transitions = transitions
  end
end

class StateName < JsonStringify
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

class Transition < JsonStringify
  attr_reader :source, :destination, :predicate
  def initialize(source, destination, predicate)
    @source = source
    @destination = destination
    @predicate = predicate
  end
end