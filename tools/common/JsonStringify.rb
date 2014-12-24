require 'oj'

class JsonStringify
  def to_s
    Oj::dump self
  end
end
