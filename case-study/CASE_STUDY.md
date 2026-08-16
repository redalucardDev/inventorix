# Case Study Scenarios to discuss

## Scenario 1: Cost Allocation and Tracking
**Situation**: The company needs to track and allocate costs accurately across different Warehouses and Stores. The costs include labor, inventory, transportation, and overhead expenses.

**Task**: Discuss the challenges in accurately tracking and allocating costs in a fulfillment environment. Think about what are important considerations for this, what are previous experiences that you have you could related to this problem and elaborate some questions and considerations

**Questions you may have and considerations:**

*Scoping questions I would ask first - they apply to all five scenarios:*

- Who is the primary consumer of this tool - finance, operations, or management - and which decision does it feed? A tool that steers weekly operations and a tool that feeds statutory accounts are not the same product.
- Which systems already hold this data (ERP, WMS, payroll), and are we allowed to become a system of record or must we stay a consumer?
- How frequently is cost information required: real time, daily, weekly, or monthly?
- Who owns each cost category and who is accountable when actual cost differs from budget?
- Which costs can be assigned directly and which require allocation ?

*Key questions on allocation itself:*

- What is the unit of allocation: a warehouse, a business unit code, a store, a product line, an order? Everything downstream depends on this choice.
- Which costs are direct and which are shared? In a colocation model several warehouses sit in one location and one warehouse serves several stores, so rent, overhead and part of the labor are inherently shared.
- What is the allocation driver for shared cost - square meters, picks, units stored, headcount hours - and who owns that rule? That is a finance decision, not an engineering one.

*Considerations:*

- **Identity and time are the core difficulty.** A warehouse is identified by its business unit code, but that code is reused when a warehouse is replaced. Every cost fact therefore needs an effective date and must be attached to a specific warehouse instance, while reports roll up by business unit code. Getting this wrong stays invisible until the first replacement.
- **Cost facts should be append-only.** Corrections are new entries, never in-place updates, so a report produced last month can be reproduced exactly. That is what makes the tool auditable.
- **Allocation rules must be versioned.** When a rule changes, past periods still have to be computable with the rule in force at the time, otherwise nobody can compare year over year.
- **Explainability.** A simple driver an operations manager can recompute in a spreadsheet gets used; a sophisticated allocation nobody understands gets challenged and then ignored.

*Previous experience I would relate to this:* on my current project I build the integration flows that carry billing, purchasing and sales data between an internal ERP and the surrounding applications. What went wrong at the start was supervision rather than transformation: integration errors were watched by the support team, who had no business guideline to judge them, so discrepancies between the two systems went unnoticed and surfaced late - by which point correcting them was expensive. We fixed it by giving the business team its own interface to review those errors and correct them close to the moment they happen. The lesson I would apply directly to a cost control tool: reconciliation between two systems is a feature to design from the start, not a support activity bolted on afterwards, and it belongs to the people who can tell whether a discrepancy is normal - which is rarely the people watching the logs.


## Scenario 2: Cost Optimization Strategies
**Situation**: The company wants to identify and implement cost optimization strategies for its fulfillment operations. The goal is to reduce overall costs without compromising service quality.

**Task**: Discuss potential cost optimization strategies for fulfillment operations and expected outcomes from that. How would you identify, prioritize and implement these strategies?

**Questions you may have and considerations:**

*Key questions:*

- What is the target metric: total cost, cost per order, or cost per unit stored? They conflict - cost per unit can improve while total cost grows.
- What defines "without compromising service quality"? I would want explicit service levels (delivery lead time, fill rate, availability) agreed up front, otherwise the constraint is unenforceable and every saving becomes arguable.
- What is the time horizon, and how reversible is each change?
- Do we have a reliable baseline? Without scenario 1 in place, no saving can be proven.

*Potential strategies, and what each one costs to act on:*

- **Warehouse-to-store assignment.** The fulfilment associations decide which warehouse serves which product for which store, and therefore drive transport cost. Optimising that assignment is a software change rather than an investment, which makes it the cheapest lever to test.
- **Capacity utilisation.** The model already carries a maximum capacity per location, and a capacity and stock per warehouse. The gap between them is money: the same fixed cost spread over 40% utilisation instead of 85% is a large difference in cost per stored unit. Colocation makes consolidation possible without leaving the area.
- **Labor.** Fixed staffing against variable demand is a recurring source of waste in fulfilment.

*How I would identify, prioritise and implement - as the engineer on the team, not as the analyst:*

- **Identify.** Finding the savings is the business's job; making them findable is mine. The tool has to expose cost per driver at warehouse and store level, so that outliers stand out instead of disappearing into an average. If it cannot answer "which warehouse costs the most per unit stored", the optimisation discussion cannot even start.
- **Prioritise.** Expected saving and confidence come from operations and finance; implementation cost and risk are what I bring to the arbitration. I would argue for reversible, software-only changes first, and say plainly when a lever needs a capex decision rather than a release.
- **Implement so the change can be measured and undone.** In backend terms: allocation and assignment rules as configuration rather than hardcoded logic, so a change is a parameter and not a deployment; the ability to apply a rule to a subset of locations or stores in order to pilot it; before and after measured in the same cost model; and guardrail metrics on service level, monitored and alerted, so a saving that quietly degrades delivery is visible early rather than discovered in a quarterly review.

*Consideration:* beware local optimisation. Cutting transport cost by holding more stock everywhere merely moves the cost to inventory holding. Any optimisation has to be judged on total cost under a service constraint - which is exactly why scenario 1 has to come first.

## Scenario 3: Integration with Financial Systems
**Situation**: The Cost Control Tool needs to integrate with existing financial systems to ensure accurate and timely cost data. The integration should support real-time data synchronization and reporting.

**Task**: Discuss the importance of integrating the Cost Control Tool with financial systems. What benefits the company would have from that and how would you ensure seamless integration and data synchronization?

**Questions you may have and considerations:**

*Key questions:*

- What is the system of record for each type of data—actual costs, budgets, forecasts, organizational structures and master data ?
- If figures differ between the Cost Control Tool and the financial system, which system is authoritative and how are discrepancies resolved?
- What data needs to be exchanged between the systems?
- What does "real-time" mean for a figure that is only final at month-end close? I would separate operational indicators (near real-time, approximate, used to steer) from financial truth (periodic, reconciled, used to report) .
- How will users know whether the data is complete and current?
- Are there accounting close periods where data must become locked?
- How will the integration be tested and validated, especially for edge cases and error handling?
- Are there existing integration standards or platforms within the company that we should reuse?

*Benefits worth stating to the business:*

- One version of the truth, so operations and finance stop arguing about whose spreadsheet is right.
- Manual reconciliation disappear, which shortens the close.
- Overspend is detected during the period instead of after it - which is the whole point of a cost *control* tool.
- Traceability from a figure in a report back to its source document - which means carrying a correlation identifier end to end through the ingestion, and is what makes the tool usable in an audit.

*Considerations on making it seamless:*

- **Asynchronous and event-driven rather than synchronously coupled.** The ERP will be unavailable or frozen for close at some point, and our tool cannot stop with it. Same reasoning as in the code base, where the legacy system is notified only after the transaction commits instead of being called inside it.
- **One adapter per external system, behind our own interface** (an anti-corruption layer), so an ERP upgrade or replacement does not ripple through the cost model. The port-and-adapter idea applied at integration level.
- **Idempotent, replayable ingestion.** Financial data will be resent; processing the same posting twice must not double count. Natural keys and deduplication from day one.
- **Contract-first interfaces.** With finance systems the interface contract is the negotiation, so I would specify and version it explicitly rather than let it emerge from the code.

## Scenario 4: Budgeting and Forecasting
**Situation**: The company needs to develop budgeting and forecasting capabilities for its fulfillment operations. The goal is to predict future costs and allocate resources effectively.

**Task**: Discuss the importance of budgeting and forecasting in fulfillment operations and what would you take into account designing a system to support accurate budgeting and forecasting?

**Questions you may have and considerations:**

*Key questions:*

- What decision does the forecast drive - headcount planning, capacity investment, pricing?
- Granularity and cadence: monthly per warehouse over a year, or weekly for labor scheduling?
- Is there already a demand or sales forecast we can consume ?
- Will anyone measure forecast quality? If error and bias are never tracked, the forecast will not improve.

*Considerations for the design:*

- **It depends entirely on scenario 1.** A forecast is only as good as the historical cost base, and if allocation rules changed mid-history, comparability is broken unless history is restated.
- **Seasonality and events** (peaks, promotions, holidays) dominate fulfilment volumes, so they belong in the model as inputs rather than as manual corrections applied to its output.
- **Versioned budgets.** An approved budget is frozen and re-forecasts are new versions - the same immutability rule as the cost facts in scenario 1. Without it the target moves and comparing actuals against it means nothing.

## Scenario 5: Cost Control in Warehouse Replacement
**Situation**: The company is planning to replace an existing Warehouse with a new one. The new Warehouse will reuse the Business Unit Code of the old Warehouse. The old Warehouse will be archived, but its cost history must be preserved.

**Task**: Discuss the cost control aspects of replacing a Warehouse. Why is it important to preserve cost history and how this relates to keeping the new Warehouse operation within budget?

**Questions you may have and considerations:**

*Key questions:*

- What was the business case that justified the replacement, and will anyone verify it afterwards?
- What unique Warehouse identifier exists independently of the Business Unit Code?
- Is there an overlap period where both warehouses operate (double rent, migration labor, temporary transport), and is that transition cost inside the business unit's budget or funded separately?
- What decision does the preserved history serve: benchmarking the new site, audit, or continuity of reporting? The answer sets the retention period and whether archived data must stay queryable online.

*Considerations:*

- **Data model implication:** the current model stores only the present state of a warehouse. Cost control needs an explicit temporal dimension and a clear separation between the long-lived business unit (the area) and the successive warehouse instances serving it. That is the first thing I would flag when scoping this work.
- **Two identities, two questions.** The business unit code is stable across a replacement, while the physical warehouse is a new one and the old is archived. Cost history must be attached to the warehouse *instance* - so "what did that building actually cost us" stays true and auditable - while reporting rolls up along the *business unit code* - so the cost trend for the area stays continuous. Keying costs only on the business unit code makes it impossible to compare the old site with the new one;
- **Time-bounding is mandatory.** Each cost fact needs an effective date and each warehouse instance an active period, otherwise costs around the switch-over date land on the wrong instance - precisely when both are running and the amounts are unusual.
- **Archiving must be non-destructive.** Archived means excluded from operations, never deleted, and reports on past periods must stay reproducible. The current model supports this by marking the old warehouse archived rather than removing it.
- **The history can be the budget baseline.** The new warehouse's budget should be built from the old one's actual cost structure, adjusted for the deliberate differences (capacity, rent, automation, location). Without that history there is no credible baseline, the new budget is a guess, and nobody can demonstrate whether the replacement delivered the savings that justified it.

## Instructions for Candidates
Before starting the case study, read the [BRIEFING.md](BRIEFING.md) to quickly understand the domain, entities, business rules, and other relevant details.

**Analyze the Scenarios**: Carefully analyze each scenario and consider the tasks provided. To make informed decisions about the project's scope and ensure valuable outcomes, what key information would you seek to gather before defining the boundaries of the work? Your goal is to bridge technical aspects with business value, bringing a high level discussion; no need to deep dive.