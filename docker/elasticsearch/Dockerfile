FROM elasticsearch:9.2.1
COPY analysis-ik-9.2.1.zip /tmp/analysis-ik.zip

# The search index mappings use ik_max_word and ik_smart analyzers.
RUN elasticsearch-plugin install --batch file:///tmp/analysis-ik.zip
