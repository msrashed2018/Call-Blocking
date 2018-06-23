CREATE DATABASE callblocking;
\c callblocking
CREATE TABLE users
(
  name varchar(20),
  MSISDN INT NOT NULL,
  address text NOT NULL,
  PRIMARY KEY (MSISDN)
);

CREATE TABLE Black
(
  Start_Time timestamp without time zone,
  ID INT NOT NULL,
  End_Time timestamp without time zone,
  MSISDN INT NOT NULL,
  PRIMARY KEY (ID),
  FOREIGN KEY (MSISDN) REFERENCES users(MSISDN)
);

CREATE TABLE White
(
  Start_Time timestamp without time zone,
  End_Time timestamp without time zone,
  ID INT NOT NULL,
  MSISDN INT NOT NULL,
  PRIMARY KEY (ID),
  FOREIGN KEY (MSISDN) REFERENCES users(MSISDN)
);

CREATE TABLE Black_Black_Numbers
(
  Black_Numbers varchar(20),
  ID INT NOT NULL,
  PRIMARY KEY (Black_Numbers, ID),
  FOREIGN KEY (ID) REFERENCES Black(ID)
);

CREATE TABLE White_White_Numbers
(
  White_Numbers varchar(20),
  ID INT NOT NULL,
  PRIMARY KEY (White_Numbers, ID),
  FOREIGN KEY (ID) REFERENCES White(ID)
);
