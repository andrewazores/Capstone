require 'rubygems'
require 'bundler/setup'

require 'rspec'
require 'oj'
require_relative '../../../convert_automaton/convert_automaton'

RSpec.configure do |c|
  c.before { allow($stdout).to receive(:puts) }
  c.before { allow($stderr).to receive(:puts) }
end

describe '#ConvertAutomaton' do
  before :each do
    @converter = ConvertAutomaton.new
  end

  it 'should raise SystemExit on success' do
    expect {
      @converter.convert File.read 'automaton'
    }.to raise_error SystemExit
  end

  it 'should exit 0 on success' do
    begin
      @converter.convert File.read 'automaton'
    rescue SystemExit => se
      expect(se.status).to eq 0
    end
  end

  it 'should raise SystemExit on failure' do
    expect {
      @converter.convert File.read 'broken_automaton'
    }.to raise_error SystemExit
  end

  it 'should exit 2 on broken_automaton (badly specified automaton) failure' do
    begin
      @converter.convert File.read 'broken_automaton'
    rescue SystemExit => se
      expect(se.status).to eq 2
    end
  end

  it 'should exit 1 on broken_automaton_3 (invalid file formatting) failure' do
    begin
      @converter.convert File.read 'broken_automaton_3'
    rescue SystemExit => se
      expect(se.status).to eq 1
    end
  end

  it 'should exit 1 on broken_automaton_2 (invalid file formatting) failure' do
    begin
      @converter.convert File.read 'broken_automaton_2'
    rescue SystemExit => se
      expect(se.status).to eq 1
    end
  end

  it 'should exit 1 on broken_automaton_4 (invalid file formatting) failure' do
    begin
      @converter.convert File.read 'broken_automaton_4'
    rescue SystemExit => se
      expect(se.status).to eq 1
    end
  end
end

describe '#StateName' do
  it 'should have label \'initial\' when type is \'I\'' do
    state = StateName.new('q0', 'I')
    expect(state.type).to eq 'initial'
  end

  it 'should have label \'partial\' when type is \'PV\'' do
    state = StateName.new('q0', 'PV')
    expect(state.type).to eq 'partial'
  end

  it 'should have label \'accept\' when type is \'A\'' do
    state = StateName.new('q0', 'A')
    expect(state.type).to eq 'accept'
  end

  it 'should have label \'reject\' when type is \'R\'' do
    state = StateName.new('q0', 'R')
    expect(state.type).to eq 'reject'
  end

  it 'should have label \'unknown\' when type is \'Z\'' do
    state = StateName.new('q0', 'Z')
    expect(state.type).to eq 'unknown'
  end
end
