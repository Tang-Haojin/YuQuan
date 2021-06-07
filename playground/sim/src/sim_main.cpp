#include "obj_dir/VTestTop.h"
#include "verilated.h"

int main(int argc, char **argv, char **env) {
	Verilated::commandArgs(argc, argv);
	VTestTop *top = new VTestTop;
	top->eval();
	delete top;
	return 0;
}
