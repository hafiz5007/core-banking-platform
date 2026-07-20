flowchart LR
    Customer[Bank Customer]
    Staff[Bank Employee / Operations Staff]
    Support[Customer Support Team]

    Core["Core Banking Platform
    Manages customers, accounts,
    balances, transactions and loans"]

    Mobile[Mobile Banking Application]
    Web[Internet Banking Portal]
    Branch[Branch Banking System]
    Payments[Payment Networks / Clearing Systems]
    Cards[Card Processing System]
    KYC[KYC / AML Service]
    Reporting[Regulatory Reporting System]
    Notifications[Email / SMS Notification Service]
    Data[Data Warehouse / Analytics Platform]

    Customer -->|Uses banking services| Mobile
    Customer -->|Uses banking services| Web

    Mobile -->|Views accounts and submits transactions| Core
    Web -->|Views accounts and submits transactions| Core
    Branch -->|Creates customers and manages accounts| Core

    Staff -->|Operates and administers accounts| Branch
    Support -->|Reviews customer and transaction information| Core

    Core -->|Processes transfers and settlements| Payments
    Core -->|Processes card-related account entries| Cards
    Core -->|Performs identity and compliance checks| KYC
    Core -->|Provides regulatory information| Reporting
    Core -->|Requests customer notifications| Notifications
    Core -->|Provides operational and transaction data| Data