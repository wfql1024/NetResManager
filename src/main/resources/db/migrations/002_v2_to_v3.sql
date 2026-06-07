-- Migration V2 → V3
-- Repurposes rollback_failure_reason as rollback_status:
--   NULL/'':  no rollback attempted
--   'success': rollback succeeded
--   other:     rollback failed (the text is the failure reason)
-- Decouples rollback state from operation status:
--   status='rolled_back' is no longer used; status stays 'done'/'failed'.

-- For records that were successfully rolled back (status='rolled_back', no failure reason)
UPDATE operation_records
   SET rollback_failure_reason = 'success'
 WHERE status = 'rolled_back'
   AND (rollback_failure_reason IS NULL OR rollback_failure_reason = '');

-- For records that failed rollback (status='rolled_back', has failure reason)
-- rollback_failure_reason already contains the reason; keep as is.

-- Reset status to 'done' for all rolled_back records
UPDATE operation_records
   SET status = 'done'
 WHERE status = 'rolled_back';
