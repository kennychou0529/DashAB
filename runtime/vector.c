#include <stdlib.h>
#include <stdint.h>
#include <math.h>

#include "types.h"

/*
int32_t getBooleanTypeId() {
	return BOOLEAN;
}

int32_t getCharacterTypeId() {
	return CHARACTER;
}

int32_t getIntegerTypeId() {
	return INTEGER;
}

int32_t getRealTypeId() {
	return REAL;
}
*/

int32_t min(int32_t a, int32_t b) {
	if (a < b)
		return a;
	return b;
}

int32_t max(int32_t a, int32_t b) {
	if (a > b)
		return a;
	return b;
}

// Declarations

void int_allocVector(struct Vector* vector, int32_t size);

//////////////////////////
// 	INTERVAL  	//
//////////////////////////

void int_allocInterval(struct Interval* interval, int32_t lower, int32_t upper) {
	interval->lower = lower;
	interval->upper = upper;
}

void int_IntervalAdd(struct Interval* out, struct Interval* lhs, struct Interval* rhs) {
	out->lower = lhs->lower + rhs->lower;
	out->upper = lhs->upper + rhs->upper;
}

void int_IntervalSubtract(struct Interval* out, struct Interval* lhs, struct Interval* rhs) {
	out->lower = lhs->lower - rhs->upper;
	out->upper = lhs->upper - rhs->lower;
}

void int_IntervalMultiply(struct Interval* out, struct Interval* lhs, struct Interval* rhs) {
	int a = lhs->lower;
	int b = lhs->upper;
	int c = rhs->lower;
	int d = rhs->upper;

	int ac = a*c;
	int ad = a*d;
	int bc = b*c;
	int bd = b*d;
	
	out->lower = min( min(ac, ad), min(bc, bd) );
	out->upper = max( max(ac, ad), max(bc, bd) );
}

void int_IntervalDivide(struct Interval* out, struct Interval* lhs, struct Interval* rhs) {
	int a = lhs->lower;
	int b = lhs->upper;
	int c = rhs->lower;
	int d = rhs->upper;

	int ac = a/c;
	int ad = a/d;
	int bc = b/c;
	int bd = b/d;
	
	out->lower = min( min(ac, ad), min(bc, bd) );
	out->upper = max( max(ac, ad), max(bc, bd) );
}

void int_IntervalUniaryMinus(struct Interval* out, struct Interval* lhs) {
	out->lower = -lhs->lower;
	out->upper = -lhs->upper;
}

int int_IntervalEq(struct Interval* lhs, struct Interval* rhs) {
	if (lhs->lower == rhs->lower && lhs->upper == rhs->upper)
		return 1;
	
	return 0;
}

int int_IntervalNe(struct Interval* lhs, struct Interval* rhs) {
	if (lhs->lower == rhs->lower && lhs->upper == rhs->upper)
		return 0;
	
	return 1;
}

int int_IntervalBy(struct Vector* out, struct Interval* lhs, int32_t by) {
	if (by < 1)
		return 1;
	
	int diff = lhs->upper - lhs->lower;
	int size = (int) ceil(((float)(diff + 1))/by);

	int_allocVector(out, size);

	int32_t *out_data = (int32_t*) out->data;

	for (int i = 0; i < size; i++)
		out_data[i] = lhs->lower + i*by;

	return 0;
}

//////////////////////////
// 	BOOLEAN  	//
//////////////////////////

void bool_allocVector(struct Vector* vector, int32_t size) {
	vector->size = size;
	vector->data = malloc(sizeof(int8_t) * size);
}

void bool_VectorNot(struct Vector* out, struct Vector* lhs) {
	int8_t *out_data = (int8_t*) out->data;
	int8_t *lhs_data = (int8_t*) lhs->data;
	
	for (int i = 0; i < out->size; i++)
		out_data[i] = (!lhs_data[i]) & 1;
}

void bool_VectorOrVector(struct Vector* out, struct Vector* lhs, struct Vector* rhs) {
	int8_t *out_data = (int8_t*) out->data;
	int8_t *lhs_data = (int8_t*) lhs->data;
	int8_t *rhs_data = (int8_t*) rhs->data;
	
	for (int i = 0; i < out->size; i++)
		out_data[i] = (lhs_data[i] || rhs_data[i]) & 1;
}

void bool_VectorXOrVector(struct Vector* out, struct Vector* lhs, struct Vector* rhs) {
	int8_t *out_data = (int8_t*) out->data;
	int8_t *lhs_data = (int8_t*) lhs->data;
	int8_t *rhs_data = (int8_t*) rhs->data;
	
	for (int i = 0; i < out->size; i++)
		out_data[i] = (lhs_data[i] ^ rhs_data[i]) & 1; 
}

void bool_VectorAndVector(struct Vector* out, struct Vector* lhs, struct Vector* rhs) {
	int8_t *out_data = (int8_t*) out->data;
	int8_t *lhs_data = (int8_t*) lhs->data;
	int8_t *rhs_data = (int8_t*) rhs->data;
	
	for (int i = 0; i < out->size; i++)
		out_data[i] = (lhs_data[i] && rhs_data[i]) & 1;
}

void bool_VectorOrExpr(struct Vector* out, struct Vector* lhs, int8_t rhs) {
	int8_t *out_data = (int8_t*) out->data;
	int8_t *lhs_data = (int8_t*) lhs->data;
	
	for (int i = 0; i < out->size; i++)
		out_data[i] = (lhs_data[i] || rhs) & 1;
}

void bool_VectorXOrExpr(struct Vector* out, struct Vector* lhs, int8_t rhs) {
	int8_t *out_data = (int8_t*) out->data;
	int8_t *lhs_data = (int8_t*) lhs->data;
	
	for (int i = 0; i < out->size; i++)
		out_data[i] = (lhs_data[i] ^ rhs) & 1; 
}

void bool_VectorAndExpr(struct Vector* out, struct Vector* lhs, int8_t rhs) {
	int8_t *out_data = (int8_t*) out->data;
	int8_t *lhs_data = (int8_t*) lhs->data;
	
	for (int i = 0; i < out->size; i++)
		out_data[i] = (lhs_data[i] && rhs) & 1;
}

int bool_VectorEq(struct Vector* lhs, struct Vector* rhs) {
	int8_t *lhs_data = (int8_t*) lhs->data;
	int8_t *rhs_data = (int8_t*) rhs->data;
	
	int32_t match = 0;
	for (int i = 0; i < lhs->size; i++)
		match += lhs_data[i] == rhs_data[i];

	if (match == lhs->size)
		return 1;

	return 0;
}

int bool_VectorNe(struct Vector* lhs, struct Vector* rhs) {
	int8_t *lhs_data = (int8_t*) lhs->data;
	int8_t *rhs_data = (int8_t*) rhs->data;
	
	int32_t match = 0;
	for (int i = 0; i < lhs->size; i++)
		match += lhs_data[i] == rhs_data[i];

	if (match == lhs->size)
		return 0;

	return 1;
}

//////////////////////////
// 	INTEGER  	//
//////////////////////////

void int_allocVector(struct Vector* vector, int32_t size) {
	vector->size = size;
	vector->data = malloc(sizeof(int32_t) * size);
}

//////////////////////////
// 	GENERIC  	//
//////////////////////////

int32_t getVectorSize(struct Vector* vector) {
	return vector->size;
}

void releaseVector(struct Vector* vector) {
	free(vector->data);
}
