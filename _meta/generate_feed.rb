# encoding: utf-8

require 'rubygems' unless defined? Gem
require './bundle/bundler/setup'

require 'git'
require 'date'
require 'yaml'
require 'liquid'
require 'jekyll'

class Settings
  def self.settings
    @settings ||= YAML.load IO.read './settings.yml'
  end

  def self.clone
    settings.clone
  end

  def self.[] key
    settings[key]
  end
end

module Jekyll::Filters
  def smartify input
    input
  end

  def meta_url input
    return if input.nil?
    "https://github.com/#{Settings["github_repo"]}/blob/master/_meta/#{input}"
  end

  def post_url input
    return if input.nil?
    "https://github.com/#{Settings["github_repo"]}/blob/master/#{input}"
  end
end

def get_posts g
  g.ls_files('../').values.reduce([]) { |memo, fileinfo|
    if File.extname(fileinfo[:path]) == Settings["extname"]
      raw_content = IO.read(fileinfo[:path])
      parsed = raw_content.split(/^-+$/).reject(&:empty?).map(&:strip)
      meta_yml, content = parsed.length == 1 ? ['', parsed] : parsed
      metainfo = YAML.load(meta_yml) || {}
      basename = File.basename(fileinfo[:path], Settings["extname"])
      postinfo = {
        "id" => basename,
        "url" => File.basename(fileinfo[:path]),
        "title" => basename.gsub(/(^\d{4}-\d{2}-\d{2}-|_)/, ' ').strip,
        "content" => content,
        "created_at" => g.gblob(fileinfo[:path]).log.first.author_date,
        "updated_at" => g.gblob(fileinfo[:path]).log.last.author_date,
      }.merge(metainfo)
      memo << postinfo
    end
    memo
  }.sort_by { |post| post["updated_at"] }.reverse
end

posts = get_posts Git.open('../')
site = Settings.clone
site["updated_at"] = posts.first.nil? ? DateTime.now : posts.first["updated_at"]

feed_file_content = Liquid::Template.parse(IO.read './feed.xml.tmpl').render({"site" => site, "posts" => posts}, filters: [Jekyll::Filters])
IO.write './feed.xml', feed_file_content
