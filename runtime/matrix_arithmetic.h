#include <stdio.h>
#include <stdlib.h>
#include <stdint.h>
#include <string.h>
#include <math.h>

#include "types.h"

#define MAKE_NAME(name, type) type ## _ ## name
#define NAME(name, type) MAKE_NAME(name, type)

extern TEMPLATE_TYPE NAME(mod, TEMPLATE_NAME)(TEMPLATE_TYPE, TEMPLATE_TYPE);
extern TEMPLATE_TYPE NAME(power, TEMPLATE_NAME)(TEMPLATE_TYPE, TEMPLATE_TYPE);

void NAME(MatrixAddMatrix, TEMPLATE_NAME)(struct Matrix* out, struct Matrix* lhs, struct Matrix* rhs) {
	TEMPLATE_TYPE *out_data = (TEMPLATE_TYPE*) out->data;	
	TEMPLATE_TYPE *lhs_data = (TEMPLATE_TYPE*) lhs->data;
	TEMPLATE_TYPE *rhs_data = (TEMPLATE_TYPE*) rhs->data;

	for (int i = 0; i < lhs->rows*lhs->columns; i++)
		out_data[i] = lhs_data[i] + rhs_data[i];
}

void NAME(MatrixSubtractMatrix, TEMPLATE_NAME)(struct Matrix* out, struct Matrix* lhs, struct Matrix* rhs) {
	TEMPLATE_TYPE *out_data = (TEMPLATE_TYPE*) out->data;	
	TEMPLATE_TYPE *lhs_data = (TEMPLATE_TYPE*) lhs->data;
	TEMPLATE_TYPE *rhs_data = (TEMPLATE_TYPE*) rhs->data;

	for (int i = 0; i < lhs->rows*lhs->columns; i++)
		out_data[i] = lhs_data[i] - rhs_data[i];
}

void NAME(MatrixMultiplyMatrix, TEMPLATE_NAME)(struct Matrix* out, struct Matrix* lhs, struct Matrix* rhs) {
	TEMPLATE_TYPE *out_data = (TEMPLATE_TYPE*) out->data;	
	TEMPLATE_TYPE *lhs_data = (TEMPLATE_TYPE*) lhs->data;
	TEMPLATE_TYPE *rhs_data = (TEMPLATE_TYPE*) rhs->data;

	for (int i = 0; i < lhs->rows*lhs->columns; i++)
		out_data[i] = lhs_data[i] * rhs_data[i];
}

void NAME(MatrixDivideMatrix, TEMPLATE_NAME)(struct Matrix* out, struct Matrix* lhs, struct Matrix* rhs) {
	TEMPLATE_TYPE *out_data = (TEMPLATE_TYPE*) out->data;	
	TEMPLATE_TYPE *lhs_data = (TEMPLATE_TYPE*) lhs->data;
	TEMPLATE_TYPE *rhs_data = (TEMPLATE_TYPE*) rhs->data;

	for (int i = 0; i < lhs->rows*lhs->columns; i++)
		out_data[i] = lhs_data[i] / rhs_data[i];
}

void NAME(MatrixModulusMatrix, TEMPLATE_NAME)(struct Matrix* out, struct Matrix* lhs, struct Matrix* rhs) {
	TEMPLATE_TYPE *out_data = (TEMPLATE_TYPE*) out->data;	
	TEMPLATE_TYPE *lhs_data = (TEMPLATE_TYPE*) lhs->data;
	TEMPLATE_TYPE *rhs_data = (TEMPLATE_TYPE*) rhs->data;

	for (int i = 0; i < lhs->rows*lhs->columns; i++)
		out_data[i] = NAME(mod, TEMPLATE_NAME)(lhs_data[i], rhs_data[i]);
}

void NAME(MatrixPowerMatrix, TEMPLATE_NAME)(struct Matrix* out, struct Matrix* lhs, struct Matrix* rhs) {
	TEMPLATE_TYPE *out_data = (TEMPLATE_TYPE*) out->data;	
	TEMPLATE_TYPE *lhs_data = (TEMPLATE_TYPE*) lhs->data;
	TEMPLATE_TYPE *rhs_data = (TEMPLATE_TYPE*) rhs->data;

	for (int i = 0; i < lhs->rows*lhs->columns; i++)
		out_data[i] = NAME(power, TEMPLATE_NAME)(lhs_data[i], rhs_data[i]);
}

void NAME(MatrixUniaryMinus, TEMPLATE_NAME)(struct Matrix* out, struct Matrix* lhs, struct Matrix* rhs) {
	TEMPLATE_TYPE *out_data = (TEMPLATE_TYPE*) out->data;	
	TEMPLATE_TYPE *lhs_data = (TEMPLATE_TYPE*) lhs->data;

	for (int i = 0; i < lhs->rows*lhs->columns; i++)
		out_data[i] = -lhs_data[i];
}
