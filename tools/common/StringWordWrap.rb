module StringWordWrap
  String.class_eval do
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
end
