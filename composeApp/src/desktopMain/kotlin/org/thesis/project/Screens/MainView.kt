package org.thesis.project.Screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material3.TextButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import buttonWithCheckboxSet
import org.thesis.project.Components.*
import org.thesis.project.MainPanelLayout
import org.thesis.project.Model.InterfaceModel
import org.thesis.project.Model.NiftiView
import org.thesis.project.Model.Settings
import org.thesis.project.TooltipManager
import androidx.compose.material3.LinearProgressIndicator
import cardMenu2
import kotlinx.coroutines.launch

/*
 * This file is part of [DeepTwin-X]
 * Copyright (C) 2025 [Fardis Nazemroaya Sedeh]
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 */

/**
 * Main component for displaying the nifti files
 * */
@Composable
fun imageViewer(
    interfaceModel: InterfaceModel
) {
    val selectedData by interfaceModel.imageController.selectedData.collectAsState()
    val leftPanelWidth by interfaceModel.panelLayout.leftPanelWidth.collectAsState()
    val rightPanelWidth by interfaceModel.panelLayout.rightPanelWidth.collectAsState()
    val leftPanelExpanded by interfaceModel.panelLayout.leftPanelExpanded.collectAsState()
    val rightPanelExpanded by interfaceModel.panelLayout.rightPanelExpanded.collectAsState()
    val selectedViews by interfaceModel.imageController.selectedViews.collectAsState()
    val selectedSettings by interfaceModel.selectedSettings.collectAsState()
    val fileMapping by interfaceModel.niftiRepo.fileMapping.collectAsState()
    val infoMode = interfaceModel.infoMode.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    //for popup updates
    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) {

        //When rendered
        LaunchedEffect(Unit) {
            interfaceModel.setInfoMode(false)
            TooltipManager.clearAll()

            val shouldInformState = interfaceModel.runModelIfTriggered()
            if (shouldInformState){
                scope.launch {
                    snackbarHostState.showSnackbar("Loading images... Please wait")
                }
            }
            //Set axial as true
            interfaceModel.imageController.updateSelectedViews(NiftiView.AXIAL, true)
        }

        MainPanelLayout(
            leftPanelWidth = leftPanelWidth,
            leftPanelExpanded = leftPanelExpanded,
            rightPanelWidth = rightPanelWidth,
            rightPanelExpanded = rightPanelExpanded,
            toggleLeftPanel = { interfaceModel.panelLayout.toggleLeftPanelExpanded() },
            toggleRightPanel = { interfaceModel.panelLayout.toggleRightPanelExpanded() },
            interfaceModel = interfaceModel,

            leftContent = {

                ComponentInfoBox(
                    id = "cardMenu",
                    infoMode,
                    infoText =
                        "This section displays the loaded NIfTI files. Use the checkboxes to pick what files to visualize",
                    content = {
                        cardMenu2(
                            fileKeys = fileMapping.keys.toList(),
                            selectedData = selectedData,
                            getFileMapping = interfaceModel.niftiRepo::getFileMapping,
                            onCheckboxChanged = { label, isChecked ->
                                interfaceModel.imageController.updateSelectedData(label, isChecked)
                            },
                            interfaceModel = interfaceModel
                        )
                    },
                    enabled = true,
                    arrowDirection = TooltipArrowDirection.Left
                )

                //Displays the running jobs if any
                runningModelsList(
                    interfaceModel
                )
            },
            centerContent = {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    //Box in order to keep track of the user scrolls, so the image indexes can be adjusted
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .pointerInput(Unit) {
                                awaitPointerEventScope {
                                    while (true) {
                                        val event = awaitPointerEvent()
                                        if (event.type == PointerEventType.Scroll) {
                                            val scrollDelta = event.changes.firstOrNull()?.scrollDelta ?: Offset.Zero
                                            if (selectedData.isNotEmpty()){
                                                if (scrollDelta.y > 0f) {
                                                    interfaceModel.imageController.decrementScrollPosition()
                                                } else if (scrollDelta.y < 0f) {
                                                    interfaceModel.imageController.incrementScrollPosition()
                                                }
                                            }

                                        }
                                    }
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        //Displays the images
                        imageGrid(selectedData, selectedViews, interfaceModel)
                    }

                    ComponentInfoBox(
                        id = "menuCard",
                        infoMode,
                        infoText =
                            "This is the menu toolbar, where you can pick what axis to view and measure lesions and se pixel values",
                        content = {
                            menuCard(
                                selectedViews = selectedViews,
                                interfaceModel = interfaceModel,
                                selectedSettings = selectedSettings,
                            )
                        },
                        enabled = true,
                        arrowDirection = TooltipArrowDirection.Bottom
                    )

                }
            },
            rightContent = {
                //Image index scroller
                scrollSlider(
                    selectedData = interfaceModel.imageController.selectedData,
                    scrollStep = interfaceModel.imageController.scrollStep,
                    maxIndexMap = interfaceModel.imageController.maxSelectedImageIndex,
                    onUpdate = { value -> interfaceModel.imageController.setScrollPosition(value) } //fix this bug
                )

                //Scroller for windowing, i.e clipping
                windowControls(selectedData, interfaceModel)

            }
        )
    }
}

//Displays the running inferences in a list of cards
@Composable
fun runningModelsList(interfaceModel: InterfaceModel) {
    val jobs = interfaceModel.modelRunner.progressFlows.entries.toList()

    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        jobs.forEach { (jobId, progressFlow) ->
            val jobProgress by progressFlow.collectAsState()

            standardCard(
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Job: ${jobProgress.jobId}",
                    style = MaterialTheme.typography.titleMedium
                )

                Spacer(modifier = Modifier.height(8.dp))

                LinearProgressIndicator(
                    progress = {
                        (jobProgress.step.toFloat() / jobProgress.total.toFloat()).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(modifier = Modifier.height(8.dp))

                if (jobProgress.error){
                    Text(
                        text = "Error in model inference:",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Red
                    )

                    Text(
                        text = jobProgress.status,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                else{
                    if (jobProgress.finished) {
                        Text(
                            text = "Finished inference, downloading result...",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    else if (jobProgress.total == 1) {
                        Text(
                            text = jobProgress.status,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    else{
                        Text(
                            text = "${String.format("%.1f", (jobProgress.step.toFloat() / jobProgress.total.toFloat())* 100)}% done (${jobProgress.step}/${jobProgress.total-1} steps)",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                if (!jobProgress.finished && !jobProgress.error) {
                    Button(
                        onClick = { interfaceModel.modelRunner.cancelJob(jobProgress.jobId) },
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text("Cancel")
                    }
                } else if (jobProgress.finished) {
                    Text(
                        text = "Completed!",
                        color = Color.Green,
                        modifier = Modifier.align(Alignment.End),
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                if (jobProgress.error || jobProgress.finished){
                    Button(
                        onClick = { interfaceModel.modelRunner.removeJob(jobProgress.jobId) },
                        modifier = Modifier.align(Alignment.End),
                    ) {
                        Text("Remove", color = Color.Red)
                    }
                }
            }
        }
    }
}


/**
 * The grid of images, based on the selected images
 * */
@Composable
fun imageGrid(
    selectedData: Set<String>,
    selectedViews: Set<NiftiView>,
    interfaceModel: InterfaceModel,
) {

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
    ) {
        val containerHeight = maxHeight

        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxSize()
        ) {

            //If only a few is selected, display them in a row
            if (selectedData.size * selectedViews.size < 4) {

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {

                    Row(
                        modifier = Modifier
                            .fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        selectedData.forEach { id ->
                            selectedViews.forEach { selectedView ->
                                val nifti = interfaceModel.niftiRepo.get(id)
                                if (nifti != null) {
                                    //println("Fetched nifti $nifti, With id : ${id}")
                                    val imageIndices by interfaceModel.imageController.getImageIndicesInd(nifti)
                                        .collectAsState()
                                    val currentIndex = when (selectedView) {
                                        NiftiView.AXIAL -> imageIndices.first
                                        NiftiView.CORONAL -> imageIndices.second
                                        NiftiView.SAGITTAL -> imageIndices.third
                                    }

                                    val (slices, spacingPx, modality) = interfaceModel.niftiRepo.getSliceInd(
                                        selectedView,
                                        nifti, currentIndex
                                    )



                                    Column(
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(containerHeight)
                                            .padding(8.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center
                                    ) {

                                        standardCard(
                                            modifier = Modifier
                                                .wrapContentSize()
                                                .aspectRatio(1f),

                                            contentAlignment = Alignment.CenterHorizontally,
                                            content = {
                                                Text(
                                                    text = "${selectedView.displayName} ${nifti.name} - Slice $currentIndex",
                                                    style = MaterialTheme.typography.titleSmall
                                                )

                                                //The actual bitmap image
                                                voxelImageDisplayInd(
                                                    voxelSlice = slices,
                                                    interfaceModel = interfaceModel,
                                                    modality = modality,
                                                    pixelSpacing = spacingPx,
                                                    windowing = interfaceModel.imageController.getWindowingState(id),
                                                    minIntensity = nifti.intensity_min,
                                                    maxIntensity = nifti.intensity_max,
                                                )
                                            }
                                        )
                                    }
                                }

                            }
                        }
                    }
                }
            } else {
                selectedData.forEach { id ->
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {

                        Row(
                            modifier = Modifier
                                .fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {

                            selectedViews.forEach { selectedView ->
                                val nifti = interfaceModel.niftiRepo.get(id)
                                if (nifti != null) {
                                    //println("Fetched nifti $nifti, With id : ${id}")
                                    val imageIndices by interfaceModel.imageController.getImageIndicesInd(nifti)
                                        .collectAsState()
                                    val currentIndex = when (selectedView) {
                                        NiftiView.AXIAL -> imageIndices.first
                                        NiftiView.CORONAL -> imageIndices.second
                                        NiftiView.SAGITTAL -> imageIndices.third
                                    }

                                    val (slices, spacingPx, modality) = interfaceModel.niftiRepo.getSliceInd(
                                        selectedView,
                                        nifti, currentIndex
                                    )

                                    Column(
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(containerHeight / selectedData.size)
                                            .padding(8.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center
                                    ) {

                                        standardCard(
                                            modifier = Modifier
                                                .wrapContentSize()
                                                .aspectRatio(1f),

                                            contentAlignment = Alignment.CenterHorizontally,
                                            content = {
                                                Text(
                                                    text = "${selectedView.displayName} ${nifti.name} - Slice $currentIndex",
                                                    style = MaterialTheme.typography.titleSmall
                                                )
                                                voxelImageDisplayInd(
                                                    voxelSlice = slices,
                                                    interfaceModel = interfaceModel,
                                                    modality = modality,
                                                    pixelSpacing = spacingPx,
                                                    windowing = interfaceModel.imageController.getWindowingState(id),
                                                    minIntensity = nifti.intensity_min,
                                                    maxIntensity = nifti.intensity_max
                                                )
                                            }
                                        )
                                    }
                                }

                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * The bottom - app - bar menu
 * */
@Composable
fun menuCard(
    modifier: Modifier = Modifier,
    selectedViews: Set<NiftiView>,
    interfaceModel: InterfaceModel,
    selectedSettings: Set<Settings>,
) {
    val buttonHeight = 60.dp
    val buttonWidth = 160.dp

    Card(
        modifier = modifier.padding(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.White,
            contentColor = Color.White
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 8.dp
        )
    ) {
        FlowRow(
            modifier = Modifier
                .wrapContentSize()
                .background(LocalAppColors.current.primaryBlue, shape = RoundedCornerShape(8.dp)),
            horizontalArrangement = Arrangement.spacedBy(0.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            listOf(
                NiftiView.AXIAL,
                NiftiView.CORONAL,
                NiftiView.SAGITTAL
            ).forEach { view ->
                buttonWithCheckboxSet(
                    selectedData = selectedViews,
                    label = view,
                    onCheckboxChanged = interfaceModel.imageController::updateSelectedViews,
                    modifier = Modifier
                        .width(buttonWidth)
                        .height(buttonHeight)
                )
            }

            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(buttonHeight)
                    .background(Color.Gray)
            )

            TextButton(
                modifier = Modifier
                    .width(buttonWidth)
                    .height(buttonHeight),
                onClick = {
                    val isChecked = selectedSettings.contains(Settings.MEASUREMENT)
                    interfaceModel.updateSelectedSettings(Settings.MEASUREMENT, !isChecked)
                },
                colors = ButtonDefaults.textButtonColors(
                    containerColor = if (selectedSettings.contains(Settings.MEASUREMENT)) LocalAppColors.current.buttonPressedColor else Color.Transparent,
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(0.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Straighten, contentDescription = Settings.MEASUREMENT.settingName)
                    Spacer(Modifier.width(4.dp))
                    Text(Settings.MEASUREMENT.settingName)
                }
            }

            TextButton(
                modifier = Modifier
                    .width(buttonWidth)
                    .height(buttonHeight),
                onClick = {
                    val isChecked = selectedSettings.contains(Settings.PIXEL)
                    interfaceModel.updateSelectedSettings(Settings.PIXEL, !isChecked)
                },
                colors = ButtonDefaults.textButtonColors(
                    containerColor = if (selectedSettings.contains(Settings.PIXEL)) LocalAppColors.current.buttonPressedColor else Color.Transparent,
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(0.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Edit, contentDescription = Settings.PIXEL.settingName)
                    Spacer(Modifier.width(4.dp))
                    Text(Settings.PIXEL.settingName)
                }
            }
        }
    }
}

/**
 * Creates a card for each selected data that change the windowing center and width
 * */
@Composable
fun windowControls(selectedData: Set<String>, interfaceModel: InterfaceModel) {

    selectedData.forEach { data ->
        var selectedPresetLabel by remember { mutableStateOf<String?>(null) }
        val nifti = interfaceModel.niftiRepo.get(data)
        val min = nifti?.intensity_min ?: 0f
        val max = nifti?.intensity_max ?: 1f
        val range = max - min
        val centerRange = min..range
        val widthRange = min..range


        val windowingState = interfaceModel.imageController.getWindowingState(data).value


        standardCard(
            content = {
                Text(interfaceModel.niftiRepo.getNameFromNiftiId(data), style = MaterialTheme.typography.titleMedium)
                dropDownMenuCustom(
                    label = "Select Preset",
                    selected = selectedPresetLabel ?: "",
                    options = interfaceModel.imageController.windowPresets.keys.toList(),
                    onSelected = { label ->
                        selectedPresetLabel = label
                        interfaceModel.imageController.setPreset(interfaceModel.imageController.windowPresets[label]!!, data)
                    }
                )

                scrollWithTitle(
                    title = "Window Center",
                    value = windowingState.center,
                    onValueChange = { interfaceModel.imageController.setWindowingCenter(it, data) },
                    valueRange = centerRange,
                    modifier = Modifier.fillMaxWidth()
                )

                scrollWithTitle(
                    title = "Window Width",
                    value = windowingState.width,
                    onValueChange = { interfaceModel.imageController.setWindowingWidth(it, data) },
                    valueRange = widthRange,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        )
    }
}