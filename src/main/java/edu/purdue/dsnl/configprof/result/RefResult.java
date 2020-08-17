package edu.purdue.dsnl.configprof.result;

import lombok.Value;

import java.util.List;

@Value
public class RefResult {
	int mileage;
	List<ResultMap> dummyResults;
	List<ResultMap> results;
}
