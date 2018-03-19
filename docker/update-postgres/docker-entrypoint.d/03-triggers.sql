CREATE TRIGGER updateservice_triggers BEFORE
  INSERT ON dpk_override
  FOR EACH ROW
  EXECUTE PROCEDURE removeOldUpdateServiceEntries();