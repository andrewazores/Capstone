require 'optparse'

module OptParser
  def parse_opts
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

      opt.on('-h', '--help', 'print this message') do
        puts opt
        exit
      end
    end
    opt_parser.parse!
    options
  end
end
