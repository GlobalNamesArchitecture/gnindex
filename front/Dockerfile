FROM node:8.2-slim

WORKDIR /usr/src/graphiql.io
COPY package.json server.js index.html ./
RUN npm install

EXPOSE 3000
CMD [ "npm", "start" ]
