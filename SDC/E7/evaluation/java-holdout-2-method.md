# Independent Java E7 holdout 2

Created 2026-10-03 from the public E7 specification and a fresh read-only local MySQL data read. The independent author did not read implementation, prompts, application tests, model results, or details of the previous failure/fix. The author also created holdout 1: this is independent of implementation and failure details, but not a separate author. Wording and normal-case predicates were changed. Holdout 1 was not edited.

Contains 20 normal, 8 adversarial, and 4 out-of-domain Chinese cases. Normal cases expect `OK`, including empty matches. Other cases expect `UNSUPPORTED` with no IDs. Numeric IDs are Java-long-compatible JSON integers. Page size 100 exceeds the 20 currently visible products.

Reference data was read as `id,name,price,stock` from `mall.pms_product` under `delete_status=0 AND publish_status=1`, using the UTF-8 MySQL client. Expected IDs were manually enumerated and checked against 20 separate read-only SQL statements with the same visibility conditions. SQL validation caught one omitted ID in the manual enumeration; the expected list was corrected before delivery. Literal names used `LOCATE(literal,name)>0`; numeric comparisons used strict `<`; compound conditions used `AND`. Neither the product-query implementation nor model-generated SQL served as the oracle.

All 20 final expected ID sets match SQL exactly. JSON field structure, unique case IDs, 20/8/4 counts, status consistency, and sorted unique long-compatible IDs passed validation. The database was not modified. Model execution/scoring is left to the parent after source changes have settled and tests pass. Cases are not to be consulted during implementation development before that run. No new hashes, freezes, or application gates were added.

Expected IDs reflect the local demo database at authoring time and may become stale after data changes. Results from this later evaluation should remain distinguishable from the original holdout-1 run.
