# encoding: utf-8

require 'rubygems' unless defined? Gem
require './bundle/bundler/setup'

require 'git'
require 'date'
require 'yaml'
require 'liquid'
require 'jekyll'
require 'date'
require 'kramdown'

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
  date_re = /(^\d{4}-\d{2}-\d{2})-/

  g.ls_files('../').values.reduce([]) { |memo, fileinfo|
    if File.extname(fileinfo[:path]) == Settings["extname"]
      raw_content = IO.read(fileinfo[:path])
      parsed = raw_content.split(/^-+$/m).reject(&:empty?).map(&:strip)
      meta_yml, content = parsed.length == 1 ? [''].concat(parsed) : parsed
      metainfo = YAML.load(meta_yml) || {}
      basename = File.basename(fileinfo[:path], Settings["extname"])
      default_date = DateTime.iso8601("#{basename.match(date_re)[1]}T00:00:00#{DateTime.now.zone}").to_time
      file_git_log = g.gblob(fileinfo[:path]).log
      postinfo = {
        "id" => basename,
        "url" => File.basename(fileinfo[:path]),
        "title" => basename.gsub(date_re, '').gsub(/_/, ' ').strip,
        "content" => Kramdown::Document.new(content, input: :GFM).to_html,
        "created_at" => file_git_log.first ? file_git_log.first.author_date : default_date,
        "updated_at" => file_git_log.last ? file_git_log.last.author_date : default_date,
      }.merge(metainfo)
      memo << postinfo
    end
    memo
  }.sort_by { |post| post["updated_at"] }.reverse
end

posts = get_posts Git.open('../')
site = Settings.clone
site["updated_at"] = posts.first.nil? ? DateTime.now : posts.first["updated_at"]

feed_file_content =
  Liquid::Template
    .parse(IO.read './feed.xml.tmpl')
    .render({ "site" => site, "posts" => posts },
            filters: [Jekyll::Filters])
IO.write './feed.xml', feed_file_content
