nohup temporal server start-dev --ui-port 8088 > /tmp/nohup.out &
docker compose up -d
echo http://localhost:8088
echo http://localhost:16686
echo docker compose logs -f