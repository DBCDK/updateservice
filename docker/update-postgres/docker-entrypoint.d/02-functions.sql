CREATE OR REPLACE FUNCTION removeOldUpdateServiceEntries()
  RETURNS TRIGGER AS $updateservice_triggers$
    BEGIN
      DELETE
        FROM dpk_override
        WHERE dpk_override.created_dtm < now()::timestamp - INTERVAL '24 hour';
      RETURN NEW;
    END;
$updateservice_triggers$ LANGUAGE plpgsql;