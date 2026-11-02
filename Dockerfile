FROM node:24-alpine
WORKDIR /app
COPY --chown=568:568 server.mjs ./server.mjs
RUN mkdir /data && chown 568:568 /data
USER 568:568
ENV HOST=0.0.0.0 PORT=8787 DATA_DIR=/data
EXPOSE 8787
CMD ["node", "--max-old-space-size=96", "server.mjs"]
