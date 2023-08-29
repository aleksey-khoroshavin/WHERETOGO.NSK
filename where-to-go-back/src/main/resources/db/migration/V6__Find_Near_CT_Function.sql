CREATE OR REPLACE FUNCTION FIND_NEAR_CT(
    MY_LAT DOUBLE PRECISION,
    MY_LON DOUBLE PRECISION,
    DLAT DOUBLE PRECISION,
    DLON DOUBLE PRECISION
) RETURNS INTEGER
    LANGUAGE PLPGSQL AS
$$
BEGIN
    RETURN (SELECT COUNT(*) AS X
            FROM T_PLACE
            WHERE LATITUDE BETWEEN (@MY_LAT - @DLAT)
                AND (@MY_LAT + @DLAT)
              AND LONGITUDE BETWEEN (@MY_LON - @DLON)
                AND (@MY_LON + @DLON));
END;
$$;

COMMENT ON FUNCTION FIND_NEAR_CT(
    MY_LAT DOUBLE PRECISION,
    MY_LON DOUBLE PRECISION,
    DLAT DOUBLE PRECISION,
    DLON DOUBLE PRECISION
    )
    IS 'Функция поиска мест кандидатов';