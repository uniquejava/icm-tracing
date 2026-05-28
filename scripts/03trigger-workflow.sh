curl -i -X POST http://localhost:8080/hello \
  -H "Content-Type: application/json" \
  -d '{
    "id": "1",
    "type": "CreditMgmt.CreditLimitChangeRequest"
  }'
