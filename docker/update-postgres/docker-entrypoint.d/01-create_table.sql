CREATE TABLE dpk_override(
   request_uuid CHAR(36) NOT NULL,
   created_dtm timestamp DEFAULT current_timestamp NOT NULL,
   PRIMARY KEY( request_uuid )
);